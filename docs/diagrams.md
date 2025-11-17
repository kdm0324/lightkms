# LightKMS ASCII Diagrams (v0)

> 코드 블록으로 렌더링되어 정렬이 보장됩니다.

## 0) 전체 구성 한눈에

```text
          +--------------------+
          |   Developer/App    |
          |  (uses LightKMS)   |
          +---------+----------+
                    |
     +--------------+-------------------+
     |                                  |
[Password 경로]                   [DEK(파일 Keystore) 경로]
     |                                  |
 PBKDF2 → AES-GCM                  KEK + keystore.json → DEK → AES-GCM
     |                                  |
  ENC[...]                            ENC[...]
```

## 1) Password 경로 (가장 단순)

```text
[사용자 비밀번호] + [Salt(20B)]
          |
          v
   PBKDF2(HMAC-SHA512, 120k)
          |
          v
[암호 키(AES-256)] + [IV(12B)]
          |
          v
     AES-GCM Encrypt --------------->  암호문(= ciphertext+tag)
          |
          +--> 출력 포맷:
               ENC[AES256_GCM,<ALIAS>]:Base64( salt || iv || ciphertext+tag )

복호화:
  입력: ENC[...] → Base64 decode → salt/iv/ciphertext+tag 분리
          |
          v
   PBKDF2(비밀번호, salt) → 동일한 AES 키 복원
          |
          v
     AES-GCM Decrypt --------------->  평문
```

## 2) DEK 경로 (파일 Keystore 기반, v0)

```text
사전 준비:
  - 환경변수 KEK = $LIGHTKMS_KEK
  - keystore.json (alias → 암호화된 DEK 저장)

암호화:
  [KEK] + [keystore.json] --해제--> [DEK(32B 랜덤)]
                                          |
                                          v
                                   [IV(12B)]
                                          |
                                          v
                                   AES-GCM Encrypt
                                          |
                                          v
                               암호문(= ciphertext+tag)
                                          |
                                          +--> 출력 포맷:
                                               ENC[AES256_GCM,<ALIAS>]:Base64( iv || ciphertext+tag )

복호화:
  입력: ENC[...] → Base64 decode → iv/ciphertext+tag 분리
                           |
     [KEK] + [keystore.json] --해제--> [DEK]
                           |
                           v
                    AES-GCM Decrypt --------> 평문
```

## 3) CLI 플로우 (자동 전환 로직)

```text
[사용자 CLI 입력]
       |
       +-- 환경변수 LIGHTKMS_KEK 존재? --- YES ---> DEK 경로 사용
       |                                       (LIGHTKMS_KEYSTORE 경로로 keystore.json)
       |
       +-- NO --------------------------------> Password 경로 사용
                                                (LIGHTKMS_PASSWORD > --password > 프롬프트)
```

## 4) 애플리케이션 런타임 복호화 (개념)

```text
(현재) 수동 구성:
  [애플리케이션 설정값] --->  "ENC[...]"  ----> LightKmsService.decrypt*(...) ----> 평문
                                  |
                 +----------------+--------------------+
                 |                                     |
           Password 경로                       DEK(파일 Keystore) 경로
            (KeyManager)                (런타임에 keystore.json에서 DEK 로딩)

(향후) Starter 자동 구성(예정):
  @EnableLightKms → PropertyResolver 에서 "ENC[...]" 자동 복호화
```

## 5) DEK 회전 (권장: 더 자주)

```text
현재 alias=API_KEY 의 DEK = DEK_v1 사용 중
     |
     |--(회전)--> 새 DEK_v2 생성 → keystore.json에 v2 저장(KEK로 보호)
                 |
                 +--> 새로 생성되는 암호문은 v2로 암호화
                 |
                 +--> 구 암호문(v1)은 일정 기간 병행 복호화 허용 후 점진 교체

간단한 운용 옵션:
  - 기존 alias 유지 + 내부적으로 active_version 스위치
  - 혹은 alias_new 발급 → 점진 마이그레이션 후 alias_old 폐기
```

## 6) KEK 회전 (가끔, 계획 필요)

```text
[KEK_old] 로 보호된 keystore.json
        |
        |--(회전 계획 수립/백업)--------------------------+
        v                                                 |
 keystore.json 내용을 [KEK_new] 로 재암호화(re-wrap) <----+
        |
        +-- 완료 후 KEK_old 폐기 (안전한 기간 지나면)
```

## 7) 보안 관점 요약(흐름)

```text
유출 시나리오:
  - keystore.json 단독 유출 → KEK 없이는 DEK 해제 불가 (암호문 덩어리)
  - KEK 단독 유출 → keystore.json 접근 없으면 직접 피해 제한
  - 둘 다 유출 → 즉시 KEK 회전(모든 DEK re-wrap) + 영향 범위 점검

운용 수칙:
  - KEK는 ENV/시크릿 매니저로 주입, 이미지/깃/로그에 남기지 않기
  - keystore.json 권한 600, 커밋 금지
  - 회전/백업/복구 절차 문서화 및 주기 점검
```

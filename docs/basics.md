# LightKMS Basics — 암복호화 첫걸음 (v0)

암호화가 처음인 분도 10분 안에 따라 할 수 있도록 **“설정값 암호화”**에 필요한 핵심만 정리했습니다.  
(v0: **Password 경로 + 파일 Keystore(DEK) 경로** 지원)

---

## 1) “암호화 / 복호화” 아주 간단히

- **암호화(Encrypt)**: 평문(읽을 수 있는 문자열)을 **읽을 수 없는 형태(암호문)**로 바꾸는 것
- **복호화(Decrypt)**: 암호문을 **다시 평문**으로 되돌리는 것
- **키(Key)**: 암호화/복호화에 사용하는 비밀 재료  
  (사람이 기억하는 **비밀번호**일 수도 있고, 랜덤으로 생성한 **바이너리 키**일 수도 있습니다)

> 비밀번호로 바로 암호화할 수도 있고,  
> **랜덤 키(DEK)** 로 데이터를 암호화한 뒤,  
> 그 DEK를 다시 **다른 키(KEK)** 로 감싸 보관하는 방식도 자주 사용됩니다.

---

## 2) LightKMS를 왜 쓰나요?

- 설정값(DB 비밀번호, Redis 패스워드, API 토큰 등)을 **Git에 평문으로 올리면 위험**합니다.
- LightKMS는 이런 값들을 **`ENC[...]` 문자열 형태로 암호화**해 저장하고,  
  필요할 때 **CLI나 애플리케이션 코드에서 평문으로 복원**할 수 있도록 도와줍니다.

즉, **“설정 파일 / 환경변수에 남는 시크릿”**을 다루기 위한 경량 암복호화 도구입니다.

---

## 3) 오늘 사용할 두 가지 모드

LightKMS v0에서는 다음 두 가지 모드를 제공합니다.

1. **Password 모드 (가장 단순, 학습/로컬용)**

   - 사용자가 입력한 **비밀번호**에서 암호 키를 만들어 데이터를 암호화합니다.
   - 따로 keystore 파일 없이 바로 시작할 수 있어, 구조를 이해하기 좋습니다.

2. **DEK 모드 (운영 환경에 권장)**

   - 강한 랜덤키 **DEK(Data Encryption Key)** 로 데이터를 암호화합니다.
   - 이 DEK는 **KEK(Key Encryption Key)** 로 암호화한 상태로 `keystore.json`에 저장합니다.
   - keystore 파일이 유출되더라도 **KEK가 없으면 DEK를 풀 수 없으므로**, 위험이 크게 줄어듭니다.

---

## 4) 설치 / 빌드 (한 줄)

루트 디렉터리에서 CLI를 빌드합니다(shaded JAR 생성).

```bash
mvn -q -DskipTests -pl lightkms-cli -am package
````

> Java 21 + Maven 환경이 준비되어 있어야 합니다.

---

## 5) Password 모드 — 가장 쉬운 첫 체험

### 5-1) 환경 정리

우선 KEK/Keystore 환경변수를 비워 Password 모드로 강제합니다.

```bash
unset LIGHTKMS_KEK LIGHTKMS_KEYSTORE
```

### 5-2) 암호화 (Encrypt)

```bash
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  encrypt --alias DEFAULT --password P@ss "my-db-password" \
  | tee /tmp/secret.enc
```

* 출력 예시:

  ```text
  ENC[AES256_GCM,DEFAULT]:Base64(....)
  ```

* 이 문자열(암호문)을 **YAML, 환경변수, 설정 서버** 등에 그대로 저장해 두면 됩니다.

### 5-3) 복호화 (Decrypt)

```bash
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  decrypt "$(cat /tmp/secret.enc)"
```

* 결과: `my-db-password`

> **zsh 사용자는** `ENC[...]` 전체를 반드시 `'...'` 또는 `"..."`로 감싸 주세요.
> (쉘이 `[` 를 글롭으로 인식하여 에러가 날 수 있습니다.)

### 5-4) 내부 동작 요약

* (1) **비밀번호 + Salt** → PBKDF2(HMAC-SHA512, 120,000회 반복) → **강한 키 생성**
* (2) 생성된 키로 **AES-256-GCM** 암호화 (IV 12바이트, Tag 128비트)
* (3) 결과는 다음 포맷으로 출력됩니다.

  ```text
  ENC[AES256_GCM,<ALIAS>]:Base64( salt || iv || ciphertext+tag )
  ```

---

## 6) DEK 모드 — 운영에서 추천하는 방식

### 왜 DEK 모드인가요?

* Password 모드는 **사람이 고른 비밀번호**에 안전성이 의존합니다.
* DEK 모드는 **랜덤 32바이트 키(DEK)** 로 데이터를 암호화하고,
  이 DEK를 다시 **KEK로 암호화**해 keystore 파일에 저장합니다.
* keystore 파일이 유출되더라도, KEK를 모르면 DEK를 복원하기 어려워 **위험을 크게 줄일 수 있습니다.**

### 6-1) 준비 (KEK / Keystore 생성)

```bash
# 1) KEK 설정 (예시)
export LIGHTKMS_KEK='SuperS3cr3t!'                 # KEK

# 2) keystore 초기화
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  keystore-init --path ~/.lightkms/keystore.json

chmod 600 ~/.lightkms/keystore.json                # 권한 최소화 권장

# 3) alias 추가 (DEFAULT)
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  key-add --alias DEFAULT --path ~/.lightkms/keystore.json

# 4) 환경변수에 keystore 경로 등록
export LIGHTKMS_KEYSTORE=~/.lightkms/keystore.json
```

### 6-2) 암호화 / 복호화

```bash
# 암호화
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  encrypt --alias DEFAULT "my-db-password" \
  | tee /tmp/secret2.enc

# 복호화
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  decrypt "$(cat /tmp/secret2.enc)"
```

### 6-3) 내부 동작 요약

* `keystore.json`에는 alias별 **랜덤 32바이트 DEK**가, **KEK로 암호화된 상태**로 저장됩니다.
* 실행 시 KEK로 keystore를 복호화하여 DEK를 얻고, 이 DEK로 **AES-GCM 암복호화**를 수행합니다.
* 결과 포맷(DEK 모드)은 다음과 같습니다.

  ```text
  ENC[AES256_GCM,<ALIAS>]:Base64( iv || ciphertext+tag )
  ```

  > DEK 자체가 랜덤 키이므로, 별도의 Salt/KDF 과정 없이 IV + ciphertext + tag만 저장합니다.

---

## 7) 애플리케이션(YAML)과 연동 예시

### 7-1) 암호문을 YAML에 저장

예를 들어, 암호문이 다음과 같다고 가정합니다.

```text
ENC[AES256_GCM,DEFAULT]:Base64(....)
```

이를 `application-prod.yml`에 다음처럼 넣을 수 있습니다.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.prod.internal:5432/app
    username: app_user
    password: "ENC[AES256_GCM,DEFAULT]:Base64(....)"
```

### 7-2) 애플리케이션 코드에서 복호화 (Java 예시)

> 아래 코드는 **개념 예시**입니다.
> 실제 클래스 이름/패키지 구조에 맞게 수정해서 사용하세요.

```java
import io.lightkms.core.KeyManager;
import io.lightkms.core.LightKmsService;

public class AppConfigExample {

  public static String resolve(String encOrPlain) throws Exception {
    // (Password 모드) 예시: alias -> password 매핑
    KeyManager km = new KeyManager();
    km.put("DEFAULT", System.getenv().getOrDefault("LIGHTKMS_PASSWORD", ""));

    LightKmsService kms = new LightKmsService(km);

    // encOrPlain 이 ENC[...] 형식이면 복호화, 아니면 그대로 반환
    return kms.decrypt(encOrPlain);
  }
}
```

> DEK 모드로 사용할 때는, 애플리케이션 기동 시
> `LIGHTKMS_KEK` / `LIGHTKMS_KEYSTORE`를 읽어 keystore에서 DEK를 로딩한 뒤,
> 해당 DEK를 사용하는 `decryptWithDek(...)` 형태의 API를 호출하는 식으로 확장하면 됩니다.
> (추후 Spring Boot Starter에서 이 과정을 자동화하는 것을 목표로 합니다.)

---

## 8) 자주 겪는 오류와 해결 방법

* `zsh: no matches found: ENC[...]`

  * → `ENC[...]` 전체를 `'ENC[...]'` 또는 `"ENC[...]"` 로 감싸 주세요.

* `Unable to access jarfile ...-shaded.jar`

  * → 빌드가 필요합니다:

    ```bash
    mvn -q -DskipTests -pl lightkms-cli -am package
    ```

* 복호화가 안 되고 암호문 그대로 출력됨

  * → 입력 값이 `ENC[` 로 시작하지 않으면 **평문으로 간주**하고 그대로 반환합니다.
    (설정값 일부만 암호화할 수 있도록 한 설계입니다.)

* `permission denied` 또는 접근 오류

  * → keystore 파일에 대한 권한을 확인하세요:

    ```bash
    chmod 600 ~/.lightkms/keystore.json
    ```

---

## 9) 보안 체크리스트 (필수 점검)

* [ ] 실제 **KEK / DEK / keystore.json** 을 Git에 올리지 않는다.
* [ ] `.gitignore`에 `**/keystore.json` 이 포함되어 있다.
* [ ] KEK는 **환경변수 / 시크릿 매니저**로 주입하고,
  스크립트/로그/이미지에 하드코딩하지 않는다.
* [ ] keystore 파일 권한은 **600** 수준으로 최소화되어 있다.
* [ ] DEK/KEK 회전 전략(언제, 어떻게 교체하는지)이 문서화되어 있다.

---

## 10) 한눈에 보는 흐름

### Password 모드

```text
[비밀번호 입력] --(+Salt, PBKDF2)--> [암호화 키] --AES-GCM--> [암호문]
                                                   ▲
                                      decrypt(암호문) ────────┘
```

### DEK 모드

```text
[KEK] + [keystore.json(암호화된 DEK들)] --복호화--> [DEK] --AES-GCM--> [암호문]
                                                                ▲
                                                   decrypt(암호문) ─┘
```

---

## 11) 더 배우기

* `docs/concepts.md`
  → KEK/DEK/KDF, 암호 포맷, 키 회전, 위협 모델 등을 다룹니다.
* 차기 버전(v1, 예정)
  → DB(JDBC) 기반 keystore, Spring Boot Starter 자동설정,
  클라우드 환경 연동 옵션 등을 확장할 계획입니다.

# LightKMS Concepts — 용어/원리 요약 (v0 기준)

본 문서는 LightKMS 사용 시 필요한 핵심 용어와 암복호화 동작 원리를 한 곳에 모은 정리입니다.
(v0: Password 경로 + File Keystore(DEK) 경로 지원)

---

## 1) 용어 정리 (Glossary)

* **DEK (Data Encryption Key)**
  실제 데이터를 AES-GCM으로 암·복호화하는 “데이터용 키”. 보통 256bit(32바이트) 랜덤 키를 사용하며, 주기적으로 **회전**(교체)할 수 있습니다.

* **KEK (Key Encryption Key)**
  DEK를 안전하게 보관하기 위해 DEK를 **암호화(랩, wrap)** 하는 상위 키.
  LightKMS v0에서는 사용자가 **환경변수 `LIGHTKMS_KEK`** 로 주입하는 값이 KEK입니다.

* **KDF (Key Derivation Function)**
  사람이 기억하는 비밀번호(약함)를 암호키(강함)로 변환하는 함수.
  LightKMS는 **PBKDF2(HMAC-SHA512, 120,000회)** 를 사용합니다.

* **IV / Tag (GCM)**
  GCM 모드의 초기화 벡터(IV, 12바이트)와 무결성 검증 태그(Tag, 128bit).
  위조/변조를 방지하며, 복호화 시 자동 검증됩니다.

---

## 2) 두 가지 경로 (Password vs DEK)

### 2.1 Password 경로

* 입력: 사용자 비밀번호(옵션/ENV/프롬프트) + **Salt(20B)**
* 과정: `PBKDF2(HMAC-SHA512, 120k)` → AES-GCM 키 도출 → 데이터 암호화
* 출력 포맷:

  ```
  ENC[AES256_GCM,<ALIAS>]:Base64( salt || iv || ciphertext+tag )
  ```
* 특징: KDF가 포함되므로 **salt**가 필요합니다. 단순/로컬 환경에서 빠르게 사용하기에 좋습니다.

### 2.2 File Keystore(DEK) 경로 (v0)

* 입력: `LIGHTKMS_KEK`(KEK) + `keystore.json`(암호화된 DEK 모음)
* 과정: KEK로 keystore의 **DEK(32B 랜덤)** 를 해제 → AES-GCM으로 데이터 암복호화
* 출력 포맷:

  ```
  ENC[AES256_GCM,<ALIAS>]:Base64( iv || ciphertext+tag )
  ```
* 특징: DEK 자체가 강한 랜덤 키라 **salt/KDF가 필요 없음**.
  운영/CI에서 **회전**, **권한 제어**, **유출면역(KEK 없인 무용)** 에 유리.

---

## 3) 파일 Keystore 구조(개념)

* 위치: 기본 `~/.lightkms/keystore.json` (ENV `LIGHTKMS_KEYSTORE`로 변경 가능)
* 보호: 파일 내용은 **KEK** 로 보호된 형태(암호문).
* 항목: alias별 메타데이터 + 암호화된 DEK
* 권한: `chmod 600 ~/.lightkms/keystore.json` 권장
* 백업: 동일한 **KEK** 가 있어야 복구 가능

> v0에서는 CLI(`keystore-init`, `key-add`, `key-rotate`)로 초기화/추가/회전을 수행합니다.

---

## 4) 회전(Rotation) 전략

* **DEK 회전(추천, 더 자주)**

  * 사유: 보안 규정, 키 노출 의심, 주기적 교체
  * 방법: `key-rotate`(추후 강화) 또는 새 alias 발급 → 신규 암호문 생성
  * 운영 팁: 구/신 DEK 병행 복호화 기간을 두고 점진 마이그레이션

* **KEK 회전(가끔, 계획 필요)**

  * 사유: 상위키 노출 의심, 비정상 접근 탐지, 보안정책
  * 영향: **keystore 전체**를 새 KEK로 **rewrap** 필요(백업/점검창 필수)

---

## 5) Threat Model 간단 점검

* keystore.json 유출 시? → **KEK 없이는 DEK 해제 불가**
* KEK 노출 시? → 즉시 **KEK 회전 + keystore 재암호화** 필요
* 로그/깃/이미지에 비밀 남김? → 금지(옵션/ENV/런타임 주입 사용)
* 권한 이슈? → 파일 권한 600, 접근 통제, 감사 로그

---

## 6) CLI와의 매핑

* **Password 경로**

  ```
  unset LIGHTKMS_KEK LIGHTKMS_KEYSTORE
  lightkms encrypt --alias DEFAULT --password P@ss "hello" | tee /tmp/c.txt
  lightkms decrypt "$(cat /tmp/c.txt)"
  ```

  우선순위: `LIGHTKMS_PASSWORD` > `--password` > 프롬프트(마스킹)

* **DEK 경로**

  ```
  export LIGHTKMS_KEK='SuperS3cr3t!'
  lightkms keystore-init --path ~/.lightkms/keystore.json
  lightkms key-add --alias DEFAULT --path ~/.lightkms/keystore.json
  export LIGHTKMS_KEYSTORE=~/.lightkms/keystore.json
  lightkms encrypt --alias DEFAULT "hello" | tee /tmp/c2.txt
  lightkms decrypt "$(cat /tmp/c2.txt)"
  ```

> zsh는 `ENC[...]` 인자를 반드시 따옴표로 감싸세요.

---

## 7) 체크리스트(보안/운영)

* [ ] 실제 **KEK/DEK/keystore.json** 을 깃에 올리지 않는다
* [ ] `.gitignore` 에 `**/keystore.json` 포함
* [ ] 실행/배포 스크립트에서 **ENV 기반 주입** 사용
* [ ] keystore 파일 **권한 600**
* [ ] DEK/KEK 회전 절차 문서화, 백업/복구 연습

---

## 8) 참고 파라미터 (v0 기본값)

* PBKDF2: HMAC-SHA512, **120,000회**, 키 길이 **256bit**
* AES-GCM: IV **12바이트**, Tag **128bit**
* Password 경로 **Salt 20바이트**

(필요시 향후 버전에서 옵션화 가능)

# LightKMS Concepts — 용어/원리 요약 (v0 기준)

본 문서는 LightKMS를 사용할 때 알아두면 좋은 핵심 용어와  
**암·복호화가 어떤 구조로 동작하는지**를 한 곳에 정리한 문서입니다.  
(v0: **Password 경로 + File Keystore(DEK) 경로** 지원)

---

## 1) 용어 정리 (Glossary)

- **DEK (Data Encryption Key)**  
  실제 데이터를 AES-GCM으로 암·복호화하는 “데이터용 키”.  
  보통 **256bit(32바이트) 랜덤 키**를 사용하며, 보안 정책에 따라 주기적으로 **회전(교체)**합니다.

- **KEK (Key Encryption Key)**  
  DEK를 안전하게 보관하기 위해, DEK를 다시 **암호화(랩, wrap)**하는 상위 키.  
  LightKMS v0에서는 사용자가 **환경변수 `LIGHTKMS_KEK` 로 주입하는 값**이 KEK 역할을 담당합니다.

- **KDF (Key Derivation Function)**  
  사람이 기억하는 비밀번호(상대적으로 약함)를, 암호용 키(강함)로 변환하는 함수입니다.  
  LightKMS는 **PBKDF2(HMAC-SHA512, 120,000회 반복)**를 사용합니다.

- **IV / Tag (GCM)**  
  - **IV (Initialization Vector)**: GCM 모드에서 사용하는 초기화 벡터. LightKMS는 **12바이트**를 사용합니다.
  - **Tag**: 데이터 위·변조를 검증하는 **무결성 태그(128bit)**.  
    복호화 시 Tag 검증에 실패하면 예외가 발생하고, 평문은 반환되지 않습니다.

---

## 2) 두 가지 경로 (Password vs DEK)

LightKMS v0는 동일한 `ENC[...]` 포맷을 사용하지만,  
키를 준비하는 방식에 따라 **두 가지 경로**로 동작합니다.

### 2.1 Password 경로

- **입력**  
  - 사용자 비밀번호(옵션/ENV/프롬프트)
  - Salt(20바이트 랜덤)

- **과정**  
  1. `비밀번호 + Salt` → `PBKDF2(HMAC-SHA512, 120k)` → **AES 키 도출**
  2. 도출된 키로 **AES-256-GCM 암호화**

- **출력 포맷**

  ```text
  ENC[AES256_GCM,<ALIAS>]:Base64( salt || iv || ciphertext+tag )


* **특징**

  * KDF 과정이 포함되므로 **Salt**가 반드시 필요합니다.
  * 별도 keystore 없이, **빠르게 시작하는 학습/로컬 환경**에 적합합니다.

---

### 2.2 File Keystore(DEK) 경로 (v0)

* **입력**

  * `LIGHTKMS_KEK` (KEK)
  * `keystore.json` (KEK로 암호화된 DEK 모음)

* **과정**

  1. KEK로 keystore를 복호화하여 **alias별 DEK(32바이트 랜덤)**를 얻습니다.
  2. 이 DEK로 **AES-256-GCM 암복호화**를 수행합니다.

* **출력 포맷**

  ```text
  ENC[AES256_GCM,<ALIAS>]:Base64( iv || ciphertext+tag )
  ```

* **특징**

  * DEK 자체가 강한 랜덤 키이므로, 별도의 Salt/KDF 과정이 **필요 없습니다**.
  * 운영/CI 환경에서:

    * **DEK 회전**
    * 파일 권한 제어
    * `keystore.json` 유출 시 **KEK 없으면 무용지물**
      같은 운영 패턴을 잡기 유리합니다.

---

## 3) 파일 Keystore 구조 (개념)

* **기본 위치**

  * `~/.lightkms/keystore.json`
  * 환경변수 `LIGHTKMS_KEYSTORE`로 경로 변경 가능

* **보호 방식**

  * 파일 내용은 **KEK로 암호화된 상태**로 저장됩니다.
  * alias별 메타데이터 + 암호화된 DEK가 포함됩니다.

* **권한**

  * `chmod 600 ~/.lightkms/keystore.json` 권장
    (해당 계정만 읽기/쓰기 가능하도록)

* **백업**

  * 동일한 **KEK**가 있어야 keystore를 복구할 수 있습니다.
  * KEK를 잃어버리면, keystore 안의 DEK도 복구할 수 없습니다.

> v0에서는 CLI (`keystore-init`, `key-add`, `key-rotate`)를 통해
> keystore 초기화 / DEK 추가 / 회전을 수행합니다.

---

## 4) 회전(Rotation) 전략

LightKMS는 크게 **DEK 회전**과 **KEK 회전**을 구분해서 생각하는 것을 권장합니다.

### 4.1 DEK 회전 (추천, 더 자주)

* **언제?**

  * 보안 규정 상 정기 교체 시
  * DEK 노출 의심 시
  * 서비스/테넌트 단위 분리 필요 시

* **어떻게?**

  * `key-rotate`(향후 강화 예정) 또는 새 alias 발급 후,
    신규 alias를 사용해 암호문을 새로 생성합니다.
  * 일정 기간 동안 **구/신 DEK를 동시에 복호화 가능한 상태**를 유지하며,
    점진적으로 암호문을 옮기는 전략을 사용할 수 있습니다.

### 4.2 KEK 회전 (가끔, 계획적으로)

* **언제?**

  * 상위 키(KEK) 노출이 의심될 때
  * 조직 보안 정책 상 정기 교체가 필요할 때

* **영향**

  * keystore 내 **모든 DEK**를 새 KEK로 다시 감싸는 **rewrap 작업**이 필요합니다.
  * 작업 전후로 백업/복구 테스트, 점검 창 확보가 필요합니다.

---

## 5) Threat Model 간단 점검

LightKMS를 사용할 때, 최소한 다음 질문들을 스스로 점검하면 좋습니다.

* **Q1. keystore.json 이 유출되면?**

  * A: **KEK가 없다면 DEK를 해제할 수 없습니다.**
    (단, KEK가 같은 서버/컨테이너에 평문으로 박혀 있지 않도록 주의해야 합니다.)

* **Q2. KEK가 노출되면?**

  * A: keystore 안의 DEK들을 복호화할 수 있으므로,
    즉시 **KEK 회전 + keystore 재암호화** 등의 대응이 필요합니다.

* **Q3. 로그 / Git / 이미지(캡처)에 시크릿이 남진 않나요?**

  * A:

    * `ENC[...]` 암호문은 남을 수 있지만,
    * KEK/DEK/평문 패스워드를 그대로 남기지 않도록 설계·운영해야 합니다.

* **Q4. 파일 권한은 적절한가요?**

  * A: keystore는 **600 권한**을 기본으로,
    접근 가능한 계정/프로세스를 최소화하는 것이 좋습니다.

---

## 6) CLI와의 매핑

LightKMS CLI는 위 개념을 다음과 같이 명령으로 대응시킵니다.

### 6.1 Password 경로

```bash
unset LIGHTKMS_KEK LIGHTKMS_KEYSTORE

lightkms encrypt --alias DEFAULT --password P@ss "hello" | tee /tmp/c.txt
lightkms decrypt "$(cat /tmp/c.txt)"
```

* 비밀번호 우선순위 예시

  * `LIGHTKMS_PASSWORD` (환경변수)
  * `--password` 옵션
  * 지정되지 않으면 프롬프트에서 입력(마스킹)

### 6.2 DEK 경로

```bash
export LIGHTKMS_KEK='SuperS3cr3t!'

lightkms keystore-init --path ~/.lightkms/keystore.json
lightkms key-add --alias DEFAULT --path ~/.lightkms/keystore.json

export LIGHTKMS_KEYSTORE=~/.lightkms/keystore.json

lightkms encrypt --alias DEFAULT "hello" | tee /tmp/c2.txt
lightkms decrypt "$(cat /tmp/c2.txt)"
```

> **zsh**에서는 `ENC[...]` 인자를 반드시 `'...'` 또는 `"..."`로 감싸 주세요.

---

## 7) 체크리스트 (보안 / 운영)

* [ ] **KEK / DEK / keystore.json** 을 Git 등 버전관리 시스템에 올리지 않는다.
* [ ] `.gitignore` 에 `**/keystore.json` 이 포함되어 있다.
* [ ] 실행/배포 스크립트에서 KEK는 **환경변수 / 시크릿 매니저**로 주입한다.
* [ ] keystore 파일 권한은 **600** 수준으로 제한되어 있다.
* [ ] DEK/KEK 회전 절차가 문서화되어 있고, 백업/복구 연습을 해 본 적이 있다.

---

## 8) 기본 파라미터 (v0 기준)

* **PBKDF2**

  * 알고리즘: `PBKDF2WithHmacSHA512`
  * 반복 횟수: **120,000회**
  * 키 길이: **256bit**

* **AES-GCM**

  * IV 길이: **12바이트**
  * Tag 길이: **128bit**

* **Password 경로**

  * Salt: **20바이트**

> 추후 버전에서는 일부 파라미터를 설정값으로 노출하여,
> 환경/보안 정책에 따라 조정할 수 있도록 확장할 수 있습니다.

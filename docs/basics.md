# LightKMS Basics — 암복호화 첫걸음 (v0)

암호화가 처음인 분도 10분 안에 따라 할 수 있도록 핵심만 쉽게 설명합니다.
(v0: Password 경로 + 파일 Keystore(DEK) 경로 지원)

---

## 1) “암호화/복호화” 아주 간단히

* **암호화(Encrypt)**: 평문(읽을 수 있는 문자열)을 **읽을 수 없는 형태**로 바꾸는 것
* **복호화(Decrypt)**: 암호문을 **다시 평문**으로 되돌리는 것
* **키(Key)**: 암호화/복호화에 사용하는 비밀 재료(비밀번호 또는 랜덤 키)

> 비밀번호로 바로 암호화할 수도 있고, **랜덤 키(DEK)** 로 암호화하고
> 그 DEK를 **다른 키(KEK)** 로 감싸 보관할 수도 있습니다.

---

## 2) LightKMS를 왜 쓰나요?

* 설정값(DB 비번 등)을 **Git에 평문으로 두면 위험**합니다.
* LightKMS는 값을 **ENC[...]** 형태로 안전하게 저장하고,
  필요할 때 **CLI나 앱 코드에서 평문으로 복원**할 수 있게 도와줍니다.

---

## 3) 오늘 쓸 두 가지 모드

1. **Password 모드(가장 쉬움)**

  * 내 비밀번호에서 암호 키를 만들어 데이터를 암호화
  * 바로 시작하기 좋음(학습/로컬)

2. **DEK 모드(운영용 추천)**

  * 강한 랜덤키 **DEK**로 데이터를 암호화
  * 그 DEK는 **KEK**로 감싼 뒤 파일에 보관(keystore.json)
  * 유출돼도 **KEK 없이는 DEK를 못 풂 → 안전**

---

## 4) 설치/빌드(한 줄)

루트에서 CLI를 빌드합니다(shaded JAR 생성).

```
mvn -q -DskipTests -pl lightkms-cli -am package
```

---

## 5) Password 모드 — 가장 쉬운 첫 체험

### 5-1) 준비

```
unset LIGHTKMS_KEK LIGHTKMS_KEYSTORE
```

### 5-2) 암호화(Encrypt)

```
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  encrypt --alias DEFAULT --password P@ss "my-db-password" \
  | tee /tmp/secret.enc
```

* 출력 예시:

  ```
  ENC[AES256_GCM,DEFAULT]:Base64(....)
  ```
* 이 문자열(암호문)을 **YAML/환경변수** 등에 저장해 두면 됩니다.

### 5-3) 복호화(Decrypt)

```
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  decrypt "$(cat /tmp/secret.enc)"
```

* 결과: `my-db-password`

> zsh 사용자는 `ENC[...]` 전체를 **따옴표로 감싸야** 합니다.

### 5-4) 내부에서 무슨 일이?

* 비밀번호 + Salt → **PBKDF2**(120,000회) → 강한 키 생성
* **AES-GCM**으로 암호화 (IV 12바이트, Tag 128비트)
* 결과 포맷(Password 모드):

  ```
  ENC[AES256_GCM,<ALIAS>]:Base64( salt || iv || ciphertext+tag )
  ```

---

## 6) DEK 모드 — 운영에서 추천하는 방식

### 왜 DEK 모드?

* 비밀번호 대신 **강한 랜덤키(DEK)** 로 데이터를 암호화
* 그 DEK는 **KEK**로 감싼 상태로 파일에 저장(keystore.json)
* 파일이 유출돼도 **KEK 없으면 무용** → 안전

### 6-1) 준비(KEK/Keystore)

```
export LIGHTKMS_KEK='SuperS3cr3t!'                 # KEK
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  keystore-init --path ~/.lightkms/keystore.json
chmod 600 ~/.lightkms/keystore.json

java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  key-add --alias DEFAULT --path ~/.lightkms/keystore.json

export LIGHTKMS_KEYSTORE=~/.lightkms/keystore.json  # keystore 경로
```

### 6-2) 암호화/복호화

```
# 암호화
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  encrypt --alias DEFAULT "my-db-password" | tee /tmp/secret2.enc

# 복호화
java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar \
  decrypt "$(cat /tmp/secret2.enc)"
```

### 6-3) 내부에서 무슨 일이?

* `keystore.json`에는 alias별 **랜덤 32바이트 DEK**가 **KEK로 보호된 상태**로 저장
* 실행 시 KEK로 DEK를 풀어 **AES-GCM**으로 암복호화
* 결과 포맷(DEK 모드):

  ```
  ENC[AES256_GCM,<ALIAS>]:Base64( iv || ciphertext+tag )
  ```

  (DEK는 이미 강한 랜덤키라 **Salt/KDF가 필요 없음**)

---

## 7) 앱(YAML)과의 연동 예시

### 7-1) 암호문을 YAML에 저장

```
spring:
  datasource:
    password: "ENC[AES256_GCM,DEFAULT]:Base64(...)"
```

### 7-2) 앱 코드에서 복호화(Java 예)

```java
import io.lightkms.core.KeyManager;
import io.lightkms.core.LightKmsService;

public class AppConfigExample {
  public static String resolve(String encOrPlain) throws Exception {
    // (Password 모드) 예시: alias -> password 매핑
    KeyManager km = new KeyManager();
    km.put("DEFAULT", System.getenv().getOrDefault("LIGHTKMS_PASSWORD", ""));

    LightKmsService kms = new LightKmsService(km);
    return kms.decrypt(encOrPlain); // ENC[...]면 복호화, 아니면 그대로 반환
  }
}
```

> DEK 모드로 앱에서 쓰려면, 런타임에 **keystore에서 DEK를 로딩**해 `decryptWithDek(...)`를 호출하도록 확장하면 됩니다(추후 Starter에서 자동화 예정).

---

## 8) 자주 겪는 오류

* `zsh: no matches found: ENC[...]`
  → 따옴표로 감싸세요: `'ENC[...]'` 또는 `"ENC[...]"`

* `Unable to access jarfile ...-shaded.jar`
  → 빌드가 필요합니다:
  `mvn -q -DskipTests -pl lightkms-cli -am package`

* 복호화가 안 되고 암호문 그대로 출력됨
  → 입력이 `ENC[` 로 시작하지 않으면 **평문으로 간주**하고 그대로 반환합니다.

* `permission denied` 또는 접근 오류
  → `chmod 600 ~/.lightkms/keystore.json` 권장

---

## 9) 보안 체크리스트 (필수)

* [ ] 실제 **KEK/DEK/keystore.json** 을 Git에 올리지 않는다
* [ ] `.gitignore`에 `**/keystore.json` 포함
* [ ] KEK는 **환경변수/시크릿 매니저**로 주입(로그/이미지/스크립트에 박제 금지)
* [ ] keystore 파일 권한 **600**
* [ ] 회전 전략(언제 DEK/KEK를 교체하는지) 문서화

---

## 10) 한 눈에 보는 흐름

### Password 모드

```
[비밀번호 입력] --(+Salt,KDF)--> [암호화 키] --AES-GCM--> [암호문]
                                               ▲
                                  decrypt(암호문) ────────────┘
```

### DEK 모드

```
[KEK] + [keystore.json(암호화된 DEK들)] --해제--> [DEK] --AES-GCM--> [암호문]
                                                          ▲
                                             decrypt(암호문) ───┘
```

---

## 11) 더 배우기

* `docs/concepts.md` : 용어/원리(KEK/DEK/KDF, 포맷, 회전, 위협모델)
* 차기 버전(v1): DB(JDBC) keystore, Spring Boot Starter 자동설정(예정)

# LightKMS — v0 (File Keystore + DEK auto-switch)

Spring Boot/Kubernetes 환경을 위한 경량 Key Management SDK.
PBKDF2(HMAC-SHA512) + AES-256-GCM 기반 암복호화를 제공합니다.

---

[ v0에서 완료된 것 ]

* Password 경로(옵션/ENV/프롬프트) 지원
* File Keystore(JSON) 기반 DEK 암복호화
* `LIGHTKMS_KEK` 존재 시 CLI가 자동으로 DEK 경로 사용
* `keystore-init`, `key-add`, `key-rotate` 명령 추가

---

## Quick Start

(A) Password 경로

1. 빌드(shaded JAR 포함)
   mvn -q -DskipTests -pl lightkms-cli -am package

2. 암호화
   unset LIGHTKMS_KEK LIGHTKMS_KEYSTORE
   java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar encrypt --alias DEFAULT --password P@ss "hello" | tee /tmp/c.txt

3. 복호화  (zsh는 ENC[...] 반드시 따옴표)
   java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar decrypt "$(cat /tmp/c.txt)"

(B) Keystore(DEK) 경로

1. KEK 설정
   export LIGHTKMS_KEK='SuperS3cr3t!'

2. Keystore 초기화(파일 생성)
   java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar keystore-init --path ~/.lightkms/keystore.json
   chmod 600 ~/.lightkms/keystore.json

3. Alias 생성(DEK 발급)
   java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar key-add --alias DEFAULT --path ~/.lightkms/keystore.json

4. Keystore 경로 지정
   export LIGHTKMS_KEYSTORE=~/.lightkms/keystore.json

5. 암/복호화
   java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar encrypt --alias DEFAULT "hello" | tee /tmp/c2.txt
   java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar decrypt "$(cat /tmp/c2.txt)"

## 명령 요약

* encrypt [--alias A] [--password P] PLAINTEXT
  · `LIGHTKMS_KEK` 존재 시 파일 keystore의 DEK 사용, 없으면 password 경로
* decrypt [--alias A] [--password P] "ENC[...]"
* keystore-init [--path FILE] : 새 keystore 생성
* key-add --alias A [--path FILE] : alias 생성(DEK 발급)
* key-rotate --alias A [--path FILE] : alias 회전

## 주의 사항

* zsh에서는 `ENC[...]` 인자를 반드시 따옴표로 감싸세요.
* 로컬 keystore 파일 권한을 600으로 제한하세요:
  chmod 600 ~/.lightkms/keystore.json
* `keystore.json`은 민감정보이므로 Git에 커밋하지 마세요.

## 동작 요약

* Password 경로: PBKDF2(HMAC-SHA512, 120,000회) → AES-GCM(IV 12바이트, Tag 128비트)
* DEK 경로(v0): 파일 keystore에서 alias별 DEK(32바이트) 로딩 → AES-GCM
* 출력 포맷
  · Password: ENC[AES256_GCM,<ALIAS>]:Base64(salt||iv||ciphertext+tag)
  · DEK(v0):   ENC[AES256_GCM,<ALIAS>]:Base64(iv||ciphertext+tag)

## 빌드/실행

* 전체 빌드
  mvn -q -DskipTests -am package

* CLI 도움말
  java -jar lightkms-cli/target/lightkms-cli-0.1.0-shaded.jar --help

(참고) 모듈 구성

* lightkms-core : AES/GCM + PBKDF2 코어, DEK API, File Keystore(JSON)
* lightkms-cli  : Picocli 기반 CLI (Password/Keystore 자동 전환)

## Docs
- docs/basics.md   : 처음 쓰는 사람을 위한 입문 가이드
- docs/concepts.md : 용어/원리(KEK/DEK/KDF, 포맷, 회전, 위협모델)
- docs/diagrams.md : ASCII 다이어그램 모음 (정렬 보장)

---
## Using as a library

> 아직 Maven Central 배포 전입니다. (로컬 멀티모듈/`mvn install` 기준 예시)

**Maven**
```xml
<dependency>
  <groupId>io.lightkms</groupId>
  <artifactId>lightkms-core</artifactId>
  <version>0.1.0</version>
</dependency>
````

**Java — Password 경로**

```java
import io.lightkms.core.KeyManager;
import io.lightkms.core.LightKmsService;
import io.lightkms.core.model.EncryptionResult;

KeyManager km = new KeyManager();
km.put("DEFAULT", System.getenv().getOrDefault("LIGHTKMS_PASSWORD", "")); // ENV 권장
LightKmsService kms = new LightKmsService(km);

EncryptionResult token = kms.encrypt("DEFAULT", "secret-value");
String plain = kms.decrypt(token.toString());
```

**Java — DEK(파일 keystore) 경로**

```java
import io.lightkms.core.LightKmsService;
// 아래 FileKeystore API는 프로젝트에 맞게 사용하세요 (예: read DEK by alias)
import io.lightkms.core.keystore.FileKeystore;

String kek = System.getenv("LIGHTKMS_KEK");
String ks  = System.getenv().getOrDefault("LIGHTKMS_KEYSTORE",
              System.getProperty("user.home") + "/.lightkms/keystore.json");

FileKeystore store = FileKeystore.open(ks, kek.toCharArray());
byte[] dek = store.readDek("DEFAULT");     // alias → DEK(32B)

// Service는 DEK 직접 암복호화 API 제공
LightKmsService kms = new LightKmsService(new KeyManager()); // (Password 미사용)
String enc = kms.encryptWithDek("DEFAULT", "secret-value", dek);
String plain = kms.decryptWithDek(enc, dek);
```

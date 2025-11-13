# LightKMS Core

PBKDF2(HMAC-SHA512) + AES-256-GCM 기반의 경량 암복호화 코어 모듈.

## Quick Start

### 1) Build & Test (core 모듈만)
```bash
# 방법 A: 모듈 폴더에서 단독 빌드
cd lightkms-core
mvn -q -DskipTests package
mvn -q test

# 방법 B: 루트에서 모듈만 빌드 (루트 POM의 <modules>가 등록돼 있어야 함)
cd ..
mvn -q -DskipTests -pl lightkms-core -am package
mvn -q -pl lightkms-core test
````

### 2) 사용 예시 (Java)

```java
import io.lightkms.core.KeyManager;
import io.lightkms.core.LightKmsService;
import io.lightkms.core.model.EncryptionResult;

public class Example {
  public static void main(String[] args) throws Exception {
    KeyManager km = new KeyManager();
    km.put("DB_PASS", "S3cr3t!");

    LightKmsService kms = new LightKmsService(km);

    // Encrypt
    EncryptionResult enc = kms.encrypt("DB_PASS", "my-db-password");
    System.out.println(enc.toString()); // ENC[AES256_GCM,DB_PASS]:<base64...>

    // Decrypt
    String plain = kms.decrypt(enc.toString());
    System.out.println(plain); // my-db-password
  }
}
```

### 3) 암호화 포맷 & 파라미터

* 포맷: `ENC[AES256_GCM,<ALIAS>]:<Base64(salt||iv||ciphertext+tag)>`
* PBKDF2: HMAC-SHA512, **120,000회**, 키 길이 **256bit**
* GCM: IV **12바이트**, TAG **128bit**
* Salt: **20바이트**

### 4) 모듈 구조

```
lightkms-core/
└── src/main/java/io/lightkms/core/
    ├── AESGCMEncryptor.java
    ├── KeyManager.java
    ├── LightKmsService.java
    └── model/
        ├── EncryptionResult.java
        └── KeyMetadata.java
```

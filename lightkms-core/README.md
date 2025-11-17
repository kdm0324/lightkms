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
```

### 2) 사용 예시 (Java, Password 경로)

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

### 2-1) 사용 예시 (Java, v0 DEK 경로 — 파일 Keystore로 로딩된 DEK 사용 시)

```java
import io.lightkms.core.LightKmsService;
import java.util.Base64;

public class ExampleDek {
  public static void main(String[] args) throws Exception {
    // 예: 별도 Keystore에서 로드한 32바이트 DEK
    byte[] dek = new byte[32]; // 예시: 실제로는 파일 Keystore에서 로딩
    // ... fill dek securely ...

    LightKmsService kms = new LightKmsService(alias -> "ignored".toCharArray());

    String enc = kms.encryptWithDek("DEFAULT", "hello", dek);
    System.out.println(enc); // ENC[AES256_GCM,DEFAULT]:<base64...>

    String plain = kms.decryptWithDek(enc, dek);
    System.out.println(plain); // hello
  }
}
```

## 암호화 포맷 & 파라미터

* 공통 포맷: `ENC[AES256_GCM,<ALIAS>]:<Base64(...>>`

  * Password 경로: `Base64(salt || iv || ciphertext+tag)`
  * DEK 경로(v0): `Base64(iv || ciphertext+tag)`
* PBKDF2: HMAC-SHA512, **120,000회**, 키 길이 **256bit**
* GCM: IV **12바이트**, TAG **128bit**
* Salt(Password 경로): **20바이트**

## 모듈 구조 (v0 기준)

```
lightkms-core/
└── src/main/java/io/lightkms/core/
    ├── AESGCMEncryptor.java
    ├── KeyManager.java
    ├── LightKmsService.java                 # encrypt/decrypt + encryptWithDek/decryptWithDek
    ├── crypto/
    │   └── AesGcm.java                      # DEK 직접 암복호화 유틸
    ├── keystore/
    │   └── FileKeystore.java                # (v0) JSON 파일 Keystore 백엔드
    └── model/
        ├── EncryptionResult.java
        └── KeyMetadata.java
```

# LightKMS CLI

## Quick Start

```bash
# (빌드는 루트에서)
mvn -q -DskipTests -pl lightkms-cli -am package
```

### A) Password 경로

```bash
export LIGHTKMS_PASSWORD=S3cr3t

# Encrypt (alias 기본값: DEFAULT)
java -jar target/lightkms-cli-0.1.0-shaded.jar encrypt "my-db-password"

# Decrypt  (⚠️ zsh는 [] 글롭이라 전체를 따옴표로 감싸세요)
java -jar target/lightkms-cli-0.1.0-shaded.jar \
  decrypt 'ENC[AES256_GCM,DEFAULT]:<base64...>'
```

### B) Keystore(DEK) 경로 — v0 (환경변수 존재 시 자동 전환)

```bash
# KEK 설정 및 Keystore 초기화
export LIGHTKMS_KEK='SuperS3cr3t!'
java -jar target/lightkms-cli-0.1.0-shaded.jar keystore-init --path ~/.lightkms/keystore.json
chmod 600 ~/.lightkms/keystore.json

# Alias 생성(DEK 발급)
java -jar target/lightkms-cli-0.1.0-shaded.jar key-add --alias DEFAULT --path ~/.lightkms/keystore.json

# Keystore 경로 지정
export LIGHTKMS_KEYSTORE=~/.lightkms/keystore.json

# Encrypt/Decrypt (ENV가 설정되어 있으면 자동으로 DEK 경로 사용)
java -jar target/lightkms-cli-0.1.0-shaded.jar encrypt --alias DEFAULT "hello" | tee /tmp/c.txt
java -jar target/lightkms-cli-0.1.0-shaded.jar decrypt "$(cat /tmp/c.txt)"
```

## Commands

* `encrypt`, `decrypt` : Password/Keystore **자동 전환**
* `keystore-init`, `key-add`, `key-rotate` : 파일 keystore 관리 명령

## Env

* `LIGHTKMS_KEK` : 설정 시 파일 keystore(DEK) 경로 사용 (자동 전환)
* `LIGHTKMS_KEYSTORE` : keystore 경로 (기본: `$HOME/.lightkms/keystore.json`)
* `LIGHTKMS_PASSWORD` : Password 경로에서 비밀번호 주입(우선순위: env > 옵션 > 프롬프트)

## Notes

* zsh에서는 `ENC[...]` 인자에 **따옴표** 필수
* `chmod 600 $LIGHTKMS_KEYSTORE`
* keystore 파일은 민감정보이므로 Git 커밋 금지

# LightKMS CLI

## Quick Start
```bash
export LIGHTKMS_PASSWORD=S3cr3t

# Encrypt (alias 기본값: DEFAULT)
java -jar target/lightkms-cli-0.1.0-shaded.jar encrypt "my-db-password"

# Decrypt (⚠️ zsh는 [] 글롭이라 전체를 따옴표로 감싸세요)
java -jar target/lightkms-cli-0.1.0-shaded.jar \
  decrypt 'ENC[AES256_GCM,DEFAULT]:<base64...>'

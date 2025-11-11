# LightKMS

Maven 멀티모듈 스캐폴딩 초기 커밋.  
모듈:
- `lightkms-core` : AES/GCM + PBKDF2 코어
- `lightkms-cli`  : Picocli 기반 CLI

## Build
```bash
mvn -q -DskipTests package

# Roadmap

## v0 (현재)
- Password & DEK(파일 keystore) 경로
- CLI: encrypt/decrypt + keystore-init/key-add/key-rotate
- Docs: basics/concepts/diagrams, readme library 예시
- Tests: round-trip + negative cases

## v1 (계획)
- DB keystore (MySQL/MariaDB/PostgreSQL) + Testcontainers
- Spring Boot Starter: PropertyResolver에서 ENC[...] 자동 복호화
- Key rotation: 구/신 DEK 병행 복호화 기간 지원
- CI: GitHub Actions (build/test), coverage/Jacoco

## v2+
- (선택) Maven Central 배포
- (선택) Homebrew/SDKMAN! 배포 채널
- (선택) KMS 연동 stub / FIPS 모드 문서

# Testing LightKMS

## What we test (v0)
- Round-trip (encrypt → decrypt) success
- Wrong password / wrong alias → decrypt 실패
- Tampered ciphertext → decrypt 실패

## How to run
mvn -q -pl lightkms-core test

## Notes
- GCM 무결성 태그 검증 실패 시 예외가 발생해야 합니다.
- ENC[...] 서식이 올바르지 않거나 alias를 찾지 못할 때도 예외가 발생해야 합니다.

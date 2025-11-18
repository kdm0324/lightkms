# Testing LightKMS

## What we test (v0)

- encrypt → decrypt **round-trip 성공**
- 잘못된 비밀번호 / 잘못된 alias 사용 시 **decrypt 실패**
- 중간에 조작된(ciphertext/태그 수정 등) 암호문 입력 시 **decrypt 실패**

## How to run

```bash
mvn -q -pl lightkms-core test
````

## Notes

* GCM 무결성 태그 검증에 실패하면 **예외가 발생해야 합니다.**
* `ENC[...]` 포맷이 잘못되었거나, alias 를 찾지 못하는 경우에도 **예외가 발생해야 합니다.**

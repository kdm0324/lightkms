## What
- 무엇을 변경했나요?

## Why
- 왜 이 변경이 필요한가요? (문제/목표)

## How
- 핵심 구현 요약 (모듈/클래스/커맨드 등)

## Test
- 수동/자동 테스트 방법
```bash
# 예시
mvn -q -B verify
java -jar lightkms-cli/target/lightkms-cli-*.jar --help
````

## Security

* 시크릿/키/토큰 노출 없음
* keystore 파일/샘플은 dummy인지 확인

## Docs

* README / docs 업데이트 여부: [ ] 필요없음  [ ] 했음

## Checklist

* [ ] CI 초록불
* [ ] 린트/테스트 통과
* [ ] 리뷰 포인트 표시

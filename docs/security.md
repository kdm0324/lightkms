# LightKMS Security & Operations Guide

## 1. Key hierarchy (KEK / DEK)

- **KEK (Key Encryption Key)**
  - 환경변수 `LIGHTKMS_KEK` 또는 외부 KMS/비밀 관리 시스템에서 전달되는 마스터 키.
  - 파일 keystore 내 DEK를 암호화/복호화하는 용도로만 사용.
- **DEK (Data Encryption Key)**
  - 실제 시크릿(DB 비밀번호, API 토큰 등)을 암복호화하는 대칭키.
  - alias (`DEFAULT`, `READONLY_DB` 등) 단위로 관리.
- **Keystore (JSON 파일)**
  - 여러 DEK와 메타데이터(알고리즘, 생성일, 회전 이력 등)를 담는 파일.
  - Git 저장소에는 절대 커밋하지 않고, 서버/Pod 로컬 디스크 혹은 별도 스토리지에만 존재.

## 2. Threat model (요약)

LightKMS는 다음과 같은 위협을 줄이는 것을 목표로 합니다.

- Git 리포지토리 / 설정 서버에 평문 시크릿 노출
- 로그, 스냅샷, 설정 파일 공유 시 시크릿 유출
- 여러 애플리케이션에서 시크릿 표현이 제각각인 문제

다만 다음과 같은 상황은 범위 밖입니다.

- 공격자가 서버/컨테이너에 shell 접근 권한을 가지고,
  - `LIGHTKMS_KEK` 환경변수
  - keystore 파일
  - 애플리케이션 메모리
    전부를 읽을 수 있는 경우  
    → 이 경우에는 HSM/KMS, 네트워크 분리, IAM 등 추가 통제가 필요합니다.

## 3. 운영 권장 사항

1. **권한 관리**
  - keystore 파일은 `chmod 600` 또는 그에 준하는 권한으로 제한합니다.
  - 애플리케이션 실행 계정만 읽기 가능하도록 OS 계정/그룹을 분리합니다.

2. **환경변수 주입**
  - `LIGHTKMS_KEK`는 다음과 같이 주입하는 것을 권장합니다.
    - Docker/Kubernetes: Secret → envFrom / env
    - 시스템 서비스: systemd unit의 `EnvironmentFile` 등
  - CI 로그, 애플리케이션 로그에 KEK 값이 출력되지 않도록 주의합니다.

3. **백업 & 복구**
  - keystore 파일은 정기적으로 백업하되, 백업 스토리지에 대한 암호화/접근 통제를 반드시 적용합니다.
  - KEK와 keystore 백업은 가능한 한 **서로 다른 경로**(예: 서로 다른 스토리지, 다른 IAM 정책)로 관리합니다.

4. **키 회전**
  - 주기적으로 DEK alias를 회전하고, 기존 key는 일정 기간 “decrypt 전용”으로 유지합니다.
  - 회전 정책(주기, 영향도, 롤백 방법)을 팀/프로젝트 Runbook에 문서화해 두는 것을 권장합니다.

## 4. 금지/주의 영역

- 금융, 전자서명, 규제 산업 등에서 **HSM/KMS 의무가 있는 경우**  
  → LightKMS 단독 사용은 적절하지 않을 수 있으며, 반드시 보안팀/규제 검토를 거쳐야 합니다.
- KEK를 애플리케이션 코드에 하드코딩하거나, Git 리포지토리/위키에 평문으로 적어두는 행위는 금지합니다.
- 운영 환경의 KEK와 동일한 값을 개발/테스트 환경에서 재사용하지 않습니다.

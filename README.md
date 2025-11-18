# LightKMS

> Lightweight AES-256-GCM + PBKDF2 key management library & CLI for encrypting application config secrets  
> (DB password, Redis, external API tokens, etc.)

LightKMS는 애플리케이션 설정 파일에 평문으로 두기 부담스러운 값들을  
**표준화된 `ENC[...]` 포맷으로 암호화**하고, 런타임에 안전한 기본값으로 복호화하는 것을 목표로 합니다.

- 🔐 **Algorithms**: AES-256-GCM + PBKDF2(HMAC-SHA512, high iteration count)
- 🧱 **Use case**: `application.yml` / K8s ConfigMap에 저장하는 시크릿 값 보호
- 🧰 **Modules**: `lightkms-core` (library) + `lightkms-cli` (CLI)
- 🌱 **Target**: Java 21, Spring Boot 3.x, Kubernetes 환경에서의 경량 시크릿 관리

---

## TL;DR — Encrypting config secrets

가장 흔한 사용 사례는 **DataSource / Redis / 외부 API 키** 등의 설정값을 암호화해서  
Git 리포지토리나 설정 서버에 평문이 남지 않게 하는 것입니다.

### 1) CLI로 시크릿 값 암호화

```bash
# keystore + KEK 환경변수가 설정된 상태에서
$ lightkms encrypt --alias DEFAULT "super-secret-password"

ENC[AES256_GCM,]:Base64(....ciphertext+tag....)
````

### 2) 설정 파일에 `ENC[...]` 포맷으로 저장

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.prod.internal:5432/app
    username: app_user
    password: ENC[AES256_GCM,]:Base64(....)
```

### 3) 애플리케이션에서 복호화

```java
// 예시용 코드입니다. 실제 공개 API 이름에 맞게 수정해주세요.
LightKmsClient kms = LightKmsClient.builder()
    // 환경변수/설정으로부터 KEK 및 keystore 위치를 읽어오는 빌더
    .fromEnvironment() // e.g. LIGHTKMS_KEK, LIGHTKMS_KEYSTORE
    .build();

String rawPassword = kms.decryptIfWrapped(
    System.getenv("SPRING_DATASOURCE_PASSWORD")  // or loaded from PropertySource
);

// 이후 DataSource 설정 등에 rawPassword 사용
```

* `ENC[...]` 형식이 아니면 입력 값을 그대로 반환하고,
* `ENC[...]` 형식이면 AES-GCM + PBKDF2 설정에 따라 복호화하는 식으로 동작하도록 설계했습니다.

> ※ Spring Boot용 starter는 v1 범위에서 제공할 예정이며,
> 현재(v0)는 라이브러리 + CLI 형태로 기본 기능에 집중하고 있습니다.

---

## Features

* ✅ **AES-256-GCM** 기반 대칭키 암복호화
* ✅ **PBKDF2(HMAC-SHA512)** 기반 키 유도 (높은 반복 횟수, 고정된 salt/IV 길이)
* ✅ `ENC[AES256_GCM,]:Base64(...)` **일관된 출력 포맷**
* ✅ **두 가지 경로 지원**

  * 패스워드 기반 (password-based) 경로
  * 파일 keystore + DEK 기반 경로
* ✅ **환경 변수 친화적 설계**

  * `LIGHTKMS_KEK` 및 `LIGHTKMS_KEYSTORE` 환경변수 기반 자동 동작
* ✅ **Picocli 기반 CLI**

  * `encrypt`, `decrypt`
  * `keystore-init`, `key-add`, `key-rotate`

---

## Architecture Overview

LightKMS는 크게 두 가지 레이어로 구성되어 있습니다.

1. **Core (`lightkms-core`)**

  * AES-GCM 암복호화
  * PBKDF2 기반 KEK/DEK 파생
  * 파일 keystore(JSON)의 읽기/쓰기 및 alias 관리
2. **CLI (`lightkms-cli`)**

  * 시크릿 값을 암호화/복호화하는 커맨드
  * keystore 초기화 및 키 회전 명령

자세한 구조와 다이어그램은 아래 문서를 참고하세요.

---

## 📚 Documentation

* [Basics — Getting Started](docs/basics.md)
  설치, 빌드, CLI 사용법, keystore 위치, 환경변수 설정 예제를 다룹니다.

* [Concepts — KEK / DEK / PBKDF2 / Threat Model](docs/concepts.md)
  LightKMS가 사용하는 암호 알고리즘, 키 계층 구조, 위협 모델,
  어떤 상황에서 도움이 되는지 / 어떤 상황에서는 추가적인 보안 장치가 필요한지 설명합니다.

* [Architecture & Diagrams](docs/diagrams.md)
  애플리케이션, LightKMS core, CLI, 파일 keystore 사이의 관계를
  텍스트 다이어그램으로 정리했습니다.

---

## Threat Model & Scope

LightKMS는 다음과 같은 상황에서의 사용을 주요 목표로 합니다.

* Git 리포지토리, 설정 파일, 설정 서버에 **평문 시크릿이 직접 노출되지 않게** 하고 싶은 경우
* 스냅샷/스크린샷/로그 등에 시크릿 값이 평문으로 찍히는 것을 줄이고 싶은 경우
* 애플리케이션 내부에서 일관된 `ENC[...]` 포맷으로 시크릿을 관리하고 싶은 경우

다음과 같은 점은 염두에 두어야 합니다.

* LightKMS는 **애플리케이션 레벨의 경량 암호화 도구이며, HSM/KMS를 대체하는 솔루션이 아닙니다.**
* 서버/컨테이너에 직접 접근할 수 있는 공격자가 KEK, keystore 파일, 런타임 메모리를 모두 읽을 수 있는 상황에서는
  추가적인 보안 장치(네트워크 분리, HSM/KMS, 접근 제어 등)가 필요합니다.
* 실제 운영 환경에서 사용할 경우, 조직의 보안 정책 및 규제 요구사항에 따른 검토가 필요합니다.

---

## Roadmap (v0 → v1)

* v0

  * core + CLI
  * 파일 keystore, alias 기반 키 관리
  * 기본적인 테스트 및 문서 제공
* v1 (planned)

  * Spring Boot starter 제공 (PropertySource/Environment 연동)
  * keystore 포맷 버전 관리 및 마이그레이션 도구
  * 추가 KMS 연동 옵션 설계 (예: 클라우드 KMS와의 브릿지 등)

---

## License

Apache License 2.0

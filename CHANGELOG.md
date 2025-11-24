# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.1] - 2025-11-23
### Added
- File-based keystore (v0) support with alias management.
- CLI commands for keystore lifecycle: `keystore-init`, `key-add`, `key-rotate`.
- GitHub Actions workflows for CI, Release, and CodeQL security scanning.
- Documentation pages for Basics, Concepts (KEK/DEK/PBKDF2), and Architecture.

### Changed
- README 구조 정리 및 Threat Model / Scope 섹션 보강.

## [0.1.0] - 2025-11-18
### Added
- Initial AES-256-GCM + PBKDF2-based encryption/decryption implementation.
- Password-based key derivation (`PasswordAesGcmEncryptor`) and simple CLI commands:
  - `encrypt`, `decrypt`
- Maven multi-module 구조: `lightkms-core`, `lightkms-cli`.

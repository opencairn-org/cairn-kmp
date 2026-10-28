# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Before 1.0.0 a minor bump may contain breaking changes.

## [Unreleased]

### Added

- Kotlin Multiplatform project skeleton (Android, desktop, iOS) with the quality gates,
  task runner, CI and fleetlint policy shared with the other cairn repositories.
- Connect-protocol transport over Ktor for the Wire-generated `cairn.v1` stubs, with error
  mapping and a one-shot retry after a token refresh.
- Sign-in: server discovery (`GetInstanceInfo` + OAuth metadata), dynamic client registration,
  authorization code + PKCE in the system browser, personal-access-token fallback, token
  refresh with rotation, sign-out with revocation; tokens in Keychain / Keystore-encrypted
  preferences / a 0600 file; accounts and servers in SQLDelight.

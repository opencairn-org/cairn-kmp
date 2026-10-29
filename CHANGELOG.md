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
- Overview (lifetime totals, this and last week, home feed with Mine/Friends/Federated chips),
  activity list with facets, date presets, flags, sort and cursor paging, and the activity
  detail page (header, badges, gear, primary and secondary stats, static map, photos, best
  efforts, description, sources). Everything renders from the local cache first and refreshes
  from the server (`SearchActivities`, `GetActivity`, `ListBestEfforts`, `ListAttachments`,
  `StatsService.GetOverview`, `SocialService.ListHomeFeed`).
- Interactive MapLibre route map on the activity detail page (track with casing, start and finish
  markers, cursor marker) and a full-screen map view; stream charts (Vico) per channel with a
  shared cursor that moves the map marker, and a full-screen streams view.
- `make e2e`: end-to-end run of the app graph against a live cairn-core; debug builds allow
  cleartext HTTP to localhost for emulator testing.
- App shell: bottom navigation (Overview, Activities, Settings) on Navigation 3, settings with
  account and server information and sign-out.

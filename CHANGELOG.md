# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Before 1.0.0 a minor bump may contain breaking changes.

## [Unreleased]

## [0.2.0] - 2026-11-20

Live updates need a cairn-core that streams: releases up to 0.7.3 answer every server stream with
an error, so against them the screens refresh when opened but not on their own. Everything else
works with cairn-core 0.6.0 or later.

### Added

- Activity detail shows laps, time in heart-rate and power zones with aerobic decoupling, segment
  efforts with the personal rank, the other activities over the same route with the standing among
  them, kudos and comments (`ListLaps`, `GetActivityZones`, `ListActivitySegmentEfforts`,
  `ListSimilarActivities`, `ListKudos`, `ListComments`). Each section appears when the activity has
  the data.
- Export from the activity page as GPX, TCX or FIT, for activities with recorded data: the app
  asks the server for a signed link (`ExportActivity`) and hands it to the system.
- Analysis tab: training load (fitness, fatigue, form) over 3 months, 6 months, a year or
  everything with a crosshair readout, lifetime statistics by year and sport with the longest
  activities, and a heatmap of every GPS track per sport (`GetTrainingLoad`, `GetStats`,
  `GetHeatmap`).
- Connections in the account area: every connected service with its state, queue, request budgets,
  import history and queue items, refreshed while on screen (`ExternalAccountService`). The
  overview says in one line when a service needs reconnecting, imports are running, a provider
  budget is used up or an importer is offline.
- Notifications in the account area with an unread badge on the avatar; a tap marks one read and
  opens its activity (`NotificationService`).
- Live updates: the overview, the activity list and an open activity reload when the server reports
  a change, and the unread badge follows arriving notifications (`StreamActivityEvents`,
  `StreamNotifications`). The transport gained Connect server streams for this.
- Update notice for the Android APK from the release page: the app asks the project's latest
  release once a day and shows a dismissible card on the overview; a switch in the settings turns
  the check off. The request carries nothing about the user or their server. Play builds and the
  desktop packages do not check.
- Screenshot tests of the main screens at phone and desktop size (`make screenshots` re-records
  the reference images).

### Changed

- The app asks for the write scopes its edits need (`gear:write`, `training:write`,
  `profile:write`, `notifications:write`) next to the read scopes. Sign out and in again once to
  grant them to an existing browser sign-in.

### Fixed

- With a browser (OAuth) sign-in, editing gear, athlete values and format preferences was refused
  by the server for lack of a write scope; only personal-access-token sign-ins could save.

## [0.1.2] - 2026-11-20

### Changed

- Dependency updates: Ktor 3.6.1, Wire 7.2.2, kotlinx-serialization 1.13.0, Metro 1.6.0 and the
  Android Gradle Plugin 9.5.1; built with Gradle 9.9.2.

## [0.1.1] - 2026-10-30

### Added

- Account area behind the avatar in the bottom bar, replacing the Settings tab: athlete profile
  (latest values of FTP, threshold, max and resting heart rate and weight at a glance; every
  parameter with its dated history, add and delete), gear (active and retired, lifetime numbers,
  create, edit, retire, delete), personal records grouped by sport with the per-record history and
  its progression chart, settings (account, editable format and units, server, device approval),
  an about page and sign-out.

### Changed

- Format and unit preferences are edited in the app and saved on the server
  (`AuthService.UpdateUserPreferences`).

## [0.1.0] - 2026-10-29

First release: a view-only client for cairn-core 0.6.0 or later, as a signed Android APK on the
GitHub release page and desktop packages for Linux, Windows and macOS.

### Added

- Protos vendored from the cairn-core `v0.6.0` release, the first tagged core that ships the whole
  handover; `make proto-check` verifies against that tag.
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
- Activity detail shows every stream channel in one normalised chart with a legend readout at
  the cursor; "See all" opens the stacked per-channel view sized to the screen. The detail
  page, the full-screen map and the streams view share one view model, so the cursor carries over.
  Tapping a legend entry hides or shows that channel in both views.
- Activity sub-pages (detail, map, streams) share a compact header with back navigation, sport
  icon, title and page name; on the detail page it slides away while scrolling.
- Cairn launcher icon (adaptive with monochrome variant and legacy densities) and the desktop
  window and package icon.
- Sign in from another device (RFC 8628 device authorization): the login screen shows an
  8-digit code and a QR code of the verification link and waits for approval; Settings gains
  "Sign in another device", where a signed-in account enters such a code, sees the requesting
  app and scopes and approves or denies it. The app now asks for the `devices:read` and
  `devices:write` scopes and re-registers a client that predates the device grant.
- `make e2e`: end-to-end run of the app graph against a live cairn-core; debug builds allow
  cleartext HTTP to localhost for emulator testing.
- App shell: bottom navigation (Overview, Activities, Settings) on Navigation 3, settings with
  account and server information and sign-out.

[Unreleased]: https://github.com/opencairn-org/cairn-kmp/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/opencairn-org/cairn-kmp/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/opencairn-org/cairn-kmp/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/opencairn-org/cairn-kmp/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/opencairn-org/cairn-kmp/releases/tag/v0.1.0

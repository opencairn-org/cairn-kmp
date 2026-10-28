# cairn-kmp implementation plan

Status: draft, 2026-10-28. See [ARCHITECTURE.md](ARCHITECTURE.md) for the decisions this plan
executes. Each phase ends with `make check` green, fleetlint clean and a CHANGELOG entry.
Phases 2 to 4 each produce a usable app; nothing is tagged 1.0.0 without an explicit decision.
The client talks proto RPCs only. Each of Phases 2 to 4 needs the matching core release from
[handover.md](handover.md) (H2, H3, H4); the client work for a phase can start against a core
branch, but the phase is only done against a released core.

## Phase 0: repository bootstrap and fleetlint (1 to 2 days)

Goal: an empty but fully gated KMP repository that builds "hello cairn" on Android, desktop and
iOS (simulator framework), with the same hygiene as cairn-core from the first commit.

1. Replace the Android-only scaffold: `settings.gradle.kts` with `build-logic` included build,
   version catalog with every version from ARCHITECTURE.md §3 (latest release of each at the
   time of the commit), Gradle wrapper 9.8.2, `gradle.properties`
   (configuration cache, parallel, Kotlin/Native caching, `kotlin.mpp.*` flags), JDK 25 pin,
   `.idea/workspace.xml` untracked, `local.properties` ignored.
2. Convention plugins in `build-logic`: `cairn.kmp.library` (android/jvm/ios targets, JDK 25 toolchain,
   explicit API off, all warnings as errors), `cairn.kmp.compose`, `cairn.android.application`,
   `cairn.quality` (Spotless+ktlint, detekt 2 with `config/detekt/detekt.yml`, Kover, jscpd
   config, dependency-analysis, license report with `config/licenses/allowed.json`).
3. Modules: `core/common`, `core/domain`, `ui/design` (theme + a placeholder screen), `app/shared`
   (Metro `AppGraph`, Navigation 3 host with one entry), `app/android`, `app/desktop`, `app/ios`
   (Xcode project generated once, committed, framework via `embedAndSignAppleFrameworkForXcode`).
4. Gradle dependency locking (`gradle.lockfile` per module, `--write-locks` only in
   `make update-locks`) and `verification-metadata.xml`.
5. Makefile with the core target names: `help tools fmt lint test cover audit build proto-check
   check check-fast mutate update-locks release clean`.
6. Repo hygiene: `.editorconfig`, `.gitattributes`, README with quickstart, LICENSE (AGPL-3.0,
   open decision 1), NOTICE, SECURITY.md (same contact as core), CONTRIBUTING.md,
   CODE_OF_CONDUCT.md (copy of core's), `.github/CODEOWNERS`, issue templates, PR template,
   CHANGELOG.md (Keep a Changelog, `[Unreleased]`), `docs/adr/0001` (record decisions) and
   `docs/adr/0002` (clean architecture + offline-first), `.gitleaksignore`.
7. Hooks: `.pre-commit-config.yaml` for prek (same hygiene set as core, gitleaks, conventional
   commits, zizmor, shellcheck, local `fleetlint check` and `make check-fast`).
8. CI: `check.yml` (ubuntu, `actions/setup-java` 25 + `gradle/actions/setup-gradle`, `make check`,
   weekly schedule, concurrency, SHA-pinned actions, `persist-credentials: false`), `ios.yml`
   (macos, compile the iOS framework, PRs touching Kotlin/Gradle only), `codeql.yml`
   (java-kotlin + actions), `renovate.json` (gradle manager, groups, lockfile maintenance,
   `kotlin`+`metro`+`compose` group, 3-day minimum age).
9. `.fleetlint.yaml` via `fleetlint init`, then fix every finding (tier 1, public). Expected
   repo-specific entries: `changelog/generator-configured` disabled like core; a `release/*`
   exception until the first tag exists; mutation gate satisfied by `make mutate` (pitest on JVM
   compilations of `core/*`).

Exit criteria: `make check` green locally and in CI; `fleetlint check` 0 errors 0 warnings;
Android debug APK installs and shows the placeholder; desktop `run` works; iOS framework builds
on the macOS runner.

## Phase 1: foundation, login, first server round trip (1 week)

Goal: log in to a cairn-core instance with OAuth 2.1 + PKCE, see "signed in as <user>", survive
app restart offline.

1. `proto/` vendoring: `scripts/sync-proto.sh <tag>` copies `cairn-core/api/proto/cairn/v1`,
   writes `PROTO_VERSION`; `make proto-check` diffs against the tag. Wire plugin generates into
   `core/network` `commonMain` (messages + service interfaces for the eight `cairn.v1` services).
2. `ConnectGrpcClient` (Wire `GrpcClient` over Ktor): unary POST, binary proto, headers, error
   JSON → `ConnectException(code, message, details)`, timeout, cancellation, HTTP/2 via the
   engines; server-streaming envelope decoding (used from Phase 4). Tests: the Connect
   conformance fixtures plus MockEngine recordings from a real core (`GetCurrentUser`, an
   `Unimplemented`, a 401).
3. `GetInstanceInfo` (handover H1) replaces `/api/version` + `/api/instance/features` on the
   server screen; until core ships it the screen shows only the OAuth discovery result.
4. `core/auth`: `ServerRegistry`, discovery (`/.well-known/oauth-authorization-server`,
   `/api/version`), dynamic client registration, `AuthFlow` port + kalinjul adapter (spike first:
   does 0.18.4 build on Kotlin 2.4? if not, in-house PKCE flow), `TokenStore` actuals (Keychain,
   Keystore+file, 0600 file), `SessionManager` with proactive refresh and single-flight,
   Ktor `Auth` plugin wiring, logout + revoke.
5. `core/database`: SQLDelight setup, driver factories, `accounts` + `servers` + `fetch_log`
   tables, per-account DB file naming, migration test.
6. `feature/login`: server URL screen (with validation and the instance version), login button,
   PAT fallback behind "advanced", error states. `feature/settings` minimal: account, server,
   logout. `app/shared`: account-scoped Metro graph, root navigation (logged-out vs logged-in),
   connectivity monitor, snackbar/error plumbing.
7. `ui/design`: dark theme matching the web (zinc + teal), typography, formatters for
   distance/pace/speed/elevation/duration/date/time honoring `Preferences` (units, date and time
   format), compose-resources strings en + de seeded from `web/messages/*.json` keys.
8. ADRs: 0003 Connect transport on Wire+Ktor, 0004 OAuth 2.1 with dynamic registration.

Exit criteria: login works on Android, desktop and iOS simulator against a local core; killing
the app and restarting offline shows the cached user; token refresh and revoked-token paths are
covered by tests; coverage floor set (start at 60 on `core/*`).

## Phase 2: activities, the core viewing experience (2 to 3 weeks)

Goal: parity with `/`, `/activities`, `/activities/[id]` and its subpages. Needs core H2.

1. Overview (`GetOverview`, `ListHomeFeed`): totals, this/last week cards linking into the
   filtered list, feed cards with static map images (Coil, signed URLs), Mine/Friends/Federated
   chips (Federated only when `GetInstanceInfo` says so).
2. Activities list (`ListActivities` with the feed's filters and facets): filter bar with type and discipline facets,
   year/preset/from-to, gear chips, tri-state flags, range filters in display units sent as SI,
   six sorts, 50 per page with offset paging, all deep-linkable through a `ActivityFilter`
   value type (also used for heatmap in Phase 3). Offline cache per filter hash.
3. Activity detail (`GetActivity`, `GetActivityStream`, `ListBestEfforts`, `ListGear`,
   `ListLaps`, `GetActivityZones`, `ListActivitySegmentEfforts`, `ListAttachments`,
   `ListKudos`, `ListComments`, `GetMergedStream`): header, badges, primary
   and secondary stats, best efforts, laps, aerobic decoupling, training zones, segment efforts
   with the elevation profile hover, description, sources list, photos grid, kudos/comments
   (view). Adaptive layout: two panes on desktop/tablet.
4. Map (`ui/map`): route with casing, start/finish markers, fit bounds, hover/touch marker synced
   with the stream chart cursor in both directions; full-screen map screen.
5. Streams (`ui/charts`): HR/power/cadence/speed/elevation series with own scales, drag zoom
   and reset, stacked per-channel full-screen variant with provenance strip when merged.
6. Similar routes (`ListSimilarActivities`) and the read-only parts of Manage
   (`GetActivityManageInfo`: field provenance, source details; `GetParsedSource`;
   `GetExportOptions` + `ExportActivity` returning a signed URL, saved via share sheet / save
   dialog).
7. Stream cache with a size budget and LRU eviction; a "downloaded for offline" indicator.

Exit criteria: every read-only element of the web activity pages has an equivalent; screenshot
tests for list and detail on phone and desktop widths; coverage floor raised.

## Phase 3: analytics and segments (1 to 2 weeks)

Goal: parity with `/heatmap`, `/analysis`, `/stats`, `/records`, `/best-efforts/*`,
`/segments`, `/segment/[id]`, `/health`, `/athlete`. Needs core H3.

1. Heatmap (`GetHeatmap`): tracks in `HeatmapMap`, the shared filter bar, tap-to-open, fit,
   truncated warning.
2. Training load (`GetTrainingLoad`): CTL/ATL/TSB cards, 3M/6M/1Y/All, three-line chart with
   crosshair readout.
3. Stats and records (`GetStats`, `ListRecords`), best-effort history
   (`GetBestEffortHistory`), grouped by type, bars over time, links to activities.
4. Segments (`SegmentService.ListSegments`, `GetSegment`, `ListSegmentEfforts`): tiles, type
   chips, cursor paging, detail with segment map, time-trend chart, efforts table.
5. Health (`MetricsService.ListMetrics` for daily series) sparkline cards with range select;
   athlete metrics (`ListMetrics` / `GetUserContext`) read view with explanations.
6. Fix the web's known unit inconsistency on the client side: all pace/speed honor the user's
   units.

## Phase 4: account, social, notifications, sync status (1 to 2 weeks)

Goal: parity with the Account and Social groups for viewing, plus the small writes a viewer
needs. Needs core H4.

1. Notifications (`ListNotifications`, `CountUnread` for the badge, unread filter,
   `MarkNotificationRead` / `MarkAllNotificationsRead` under the `notifications:write` scope
   from the handover; `StreamNotifications` for the live badge once it exists).
2. Gear (`ListGear`, `ListGearDefaults`, pending refs count): active, defaults, retired, totals,
   link to the filtered list.
3. Connections and sync status (`ExternalAccountService.ListExternalAccounts`, `GetSyncStatus`,
   `ListSyncHistory`, `ListQueueItems`): account cards polled every 5 s while visible, status
   badge, queue counts, rate-limit budgets, history, queue items.
4. Social (`SocialService`): following feed (cursor paging), clubs list and detail, profiles,
   projected activity view (`GetProjectedActivity` with `visible_categories`), shared links
   (`GetSharedActivity`, no login), review queue (`ListReviewQueue`).
5. Settings: display name/username/email, quota (`GetQuota`), units/date/time/locale
   (`UpdateUserPreferences` under `profile:write`), server and account management, about (app
   version, core version, licenses from the report).
6. Deep links: `cairn://` and https app links for activity, segment, profile, shared token.

Exit criteria: feature parity checklist (one row per web page, from the survey) is complete for
all read-only rows; accessibility pass (content descriptions, font scaling, keyboard navigation
on desktop).

## Phase 5: release engineering and 0.x releases (1 week, overlaps with 3 and 4)

1. `release.yml` on `v*` tags: `make check`, signed Android APK (keystore from secrets), SBOM
   (`cyclonedx-gradle` or syft), cosign keyless signatures, provenance attestation, GitHub release
   with the CHANGELOG section (same awk step as core), prerelease for tags with `-`.
2. Conveyor: `conveyor.conf`, `conveyor make site` on the Linux runner, GitHub Releases as the
   update site, Linux deb/tarball, Windows msix/zip (self-signed), macOS dmg (unsigned until
   certificates exist); documented install and update paths in the README.
3. iOS: `ios.yml` extended with archive + TestFlight upload when the Apple secrets exist (user
   decision; needs the developer account).
4. In-app update notice for GitHub-distributed Android builds (compare against the releases API
   once per day, opt-out in settings).
5. `make release V=v0.1.0` after Phase 2, `v0.2.0` after Phase 3, `v0.3.0` after Phase 4.
   `v1.0.0` and the Play Store listing (AAB, Play App Signing, data-safety form, screenshots) are
   a separate decision after the 0.x line has been used for a while.

## Core track

The RPCs, transport and auth changes core needs are in [handover.md](handover.md), grouped as
H1 (before Phase 1), H2, H3, H4 (before Phases 2, 3, 4) and H5 (later). Each group is one or
more core releases gated by `buf breaking`. Client phases are developed against the core branch
and accepted against the release.

## Phase 6 (post-1.0, not planned in detail)

Imports and uploads (GPX/TCX/FIT, manual activities), edits with field masks, gear management,
sync actions, kudos/comments, follow/clubs, light theme, home-screen widgets, live updates once
core streams, web/wasm target once core sends CORS headers.

## Working conventions

- Branch per task, PR for CI, fast-forward merge; commits signed with the user's SSH key,
  Conventional Commits, no AI attribution (repo rule, same as the other cairn repos).
- `make check-fast` before each commit (< 1 min: format, detekt on changed modules, JVM unit
  tests), `make check` before each push (< 10 min on CI with the Gradle cache).
- The feature parity checklist (`docs/parity.md`, created in Phase 2 from the web survey) is the
  acceptance list for 0.3.0.

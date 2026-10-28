# cairn-kmp architecture

Status: draft, 2026-10-28. Planning document; nothing below is implemented yet.
Companion: [plan.md](plan.md) (phases), ADRs will live in `docs/adr/` once Phase 0 starts.

## 1. Goal and scope

A Kotlin Multiplatform client for a cairn-core instance (v0.5.1 or newer) with feature parity
to the current SvelteKit web UI for everything that is *viewing*. Imports, uploads and edits are
out of scope until after 1.0. Targets: Android, iOS, desktop (Linux, Windows, macOS), one
Compose Multiplatform UI.

Non-goals for 1.0: web/wasm target, imports/uploads, admin pages, social writes, federation.

## 2. What the survey of cairn-core found (drives several decisions)

- **Only part of the Connect API is served.** Of the nine `cairn.v1` services, five are mounted
  and about 22 RPCs are implemented (`ActivityService`: List/Get/Update/GetActivityStream/
  ListBestEfforts; `GearService`: all; `NotificationService`: list + mark read; `AuthService`:
  GetLoginMethods/GetCurrentUser/UpdateUserPreferences; `AdminService`: ListUsers/ListOIDCClients).
  `SegmentService`, `MetricsService`, `SettingsService` and `ExternalAccountService` are not
  mounted at all. The web UI gets overview, feed, stats, analysis, records, heatmap, laps, zones,
  segments, health, connections, social and more from plain JSON handlers under `/api/*`.
- **Decision (2026-10-28):** the client uses the proto RPC API only, no REST. Everything the
  client needs that is REST-only today is specified in [handover.md](handover.md) for cairn-core
  to implement; client phases are sequenced behind the matching core releases. Core keeps its
  REST surface for the web UI and third parties; both wrap the same use cases.
- **Transport:** Connect-Go v2 handlers serve gRPC, gRPC-Web and the Connect protocol on the same
  routes. Core will add h2c so HTTP/2 works without TLS. The client talks to those handlers with
  the **Connect protocol over HTTP/2 (binary proto codec)**, not with gRPC framing: gRPC carries
  its status in HTTP trailers, which neither Ktor nor the iOS URLSession stack expose, while the
  Connect protocol carries status in the body (unary: JSON error body; streams: end-stream frame).
  This is the same choice connect-swift and connect-kotlin make for mobile. Same protos, same
  stubs, same status codes, no second API. No server-streaming RPC is implemented yet; live
  updates poll until the streaming items in the handover land.
- **Auth for native clients:** cairn-core is an OAuth 2.1 authorization server (auth-code + PKCE
  S256, refresh-token rotation, RFC 7591 dynamic client registration, RFC 8414 discovery at
  `/.well-known/oauth-authorization-server`). Bearer `cairn_at_*` tokens are accepted by both the
  Connect interceptor and the REST handlers. Personal access tokens (`cairn_pat_*`) also work.
  There is no `LoginPassword` RPC returning a token and no OIDC `id_token`.
- **Read-only tokens are enforced server-side:** a token without a write scope is rejected for
  mutating RPCs and for non-GET REST calls. This matches a viewer-only client exactly.
- **Protos are not on the BSR**; they live in `cairn-core/api/proto` and are vendored at a tag.
- **Units are SI everywhere**, display conversion is the client's job (metric/imperial, date and
  time format come from `GetCurrentUser`). Locales: de (base) and en. Theme: dark only (zinc +
  teal).

## 3. Stack (versions checked 2026-10-28)

| Concern | Choice | Version | Notes |
|---|---|---|---|
| Language / build | Kotlin, Gradle wrapper, JDK toolchain | 2.4.21 / 9.8.2 / JDK 25 | JDK 25 is the current LTS (26 and 27 are short-term releases); pinned in `gradle/gradle-daemon-jvm.properties` + `jvmToolchain(25)`. Android bytecode target stays at 21 (what D8/R8 in AGP 9 consume). Policy: always the latest release of every library and tool; Renovate keeps it that way. |
| Android | AGP, compileSdk 37, minSdk 26 | 9.3.3 | minSdk 26 for Keystore AES-GCM and `java.time` without desugaring. |
| UI | Compose Multiplatform, Material 3, material3-adaptive | 1.12.2 | Adaptive list-detail layouts for tablet/desktop. |
| Navigation | JetBrains Navigation 3 (multiplatform) | 1.2.0-rc01 | Decided. Typed keys, back stack as state. |
| State | ViewModel (JetBrains lifecycle) + `StateFlow` | 2.11.0 | One ViewModel per screen, `UiState` sealed/data classes. |
| DI | Metro (compiler plugin) | 1.5.0 | Requires Kotlin >= 2.4.20; Kotlin and Metro are bumped together (Renovate group). |
| Database | SQLDelight: android-driver, native-driver, sqlite-driver (JVM) | 2.4.1 | Schema in `core/database`, migrations verified in CI (`verifySqlDelightMigration`). |
| HTTP | Ktor client: OkHttp engine (Android/JVM), Darwin (iOS) | 3.6.0 | Shared `HttpClient` with auth plugin, logging, timeouts. |
| Protobuf | Wire (Gradle plugin, `commonMain` codegen, well-known types) | 3.0.1 | Messages + service stubs for all targets. |
| Connect | Own `ConnectGrpcClient : com.squareup.wire.GrpcClient` on Ktor | — | Connect protocol: unary (`POST /cairn.v1.X/Y`, `application/proto`, `Connect-Protocol-Version: 1`, JSON error body → `ConnectException(code)`) and server streaming (`application/connect+proto` envelopes, end-stream frame). There is no KMP Connect client library (connect-kotlin is JVM/OkHttp only); the protocol is small and specified, so this ~400-line transport is tested against the Connect conformance fixtures. HTTP/2 via OkHttp (Android/JVM) and Darwin (iOS) engines. |
| Auth flow | kotlin-multiplatform-oidc (kalinjul) behind an `AuthFlow` port | 0.18.4 | Android: Custom Tabs; iOS: `ASWebAuthenticationSession`; desktop: embedded loopback server (experimental). Works as plain OAuth 2.0 with manually configured endpoints. Pins Kotlin 2.3.0 today → spike in Phase 1; if it does not build on 2.4, the fallback is an in-house PKCE flow (Ktor + platform browser launch, ~200 lines). |
| Secure storage | expect/actual `TokenStore` | — | iOS: Keychain. Android: AndroidKeyStore AES-GCM key + ciphertext file (Jetpack Security is deprecated). Desktop: 0600 file in the XDG/AppData config dir (Phase 1), OS keyring (Secret Service / Credential Manager / Keychain) as a hardening step later. |
| Preferences | androidx DataStore (KMP) | 1.2.x | Server URL, last account, UI prefs mirror. |
| Maps | maplibre-compose (MapLibre Native on Android/iOS/desktop) | 0.20.0 | Style `https://tiles.openfreemap.org/styles/liberty` like the web. API pre-1.0 → wrapped in one `RouteMap`/`HeatmapMap` composable so breaking renames stay local. |
| Charts | Vico (Compose Multiplatform) | 3.3.1 | Stream charts, training load, sparklines, effort bars. Elevation profile = small custom Canvas. |
| Images | Coil 3 + Ktor network fetcher | 3.6.3 | Needed for `/api/activities/{id}/map.png` and attachments, which require the bearer header. |
| Time | kotlinx-datetime | 0.8.0 | |
| Logging | Kermit | latest | |
| Tests | kotlin-test, Turbine, Ktor MockEngine, SQLDelight JDBC in-memory, compose ui-test | 1.2.1 (Turbine) | Coverage via Kover (JVM), mutation via Pitest on `core/*` JVM compilations (weekly). |
| Quality gates | Spotless+ktlint, detekt 2 (`dev.detekt`), dependency-analysis `buildHealth`, Gradle-License-Report `checkLicense`, osv-scanner on `gradle.lockfile`, jscpd, Gradle dependency locking + verification metadata, gitleaks, zizmor | 2.0.0-alpha.6 (detekt), 3.19.2, 3.1.4 | What fleetlint's Kotlin rules expect; see section 8. |
| Desktop packaging | Conveyor (free for open source; cross-builds Linux/Windows/macOS from a Linux runner; GitHub Releases as update site) | 22.x | Compose's built-in jpackage is the fallback (needs one runner per OS, no auto-update). |
| Android distribution | Signed APK on GitHub Releases now, AAB to Play after 1.0.0 | — | Same `applicationId`; distribution channel is a `BuildConfig` field so the in-app update check knows where it came from. |
| iOS distribution | TestFlight/App Store (needs the Apple Developer account); PR checks only build the simulator framework | — | Requires a macOS runner. |

Application id / package: `org.opencairn.cairn` (decided; the scaffold's
`com.github.opencairnorg.cairn` is replaced in Phase 0).

## 4. Clean architecture, module by module

Dependency rule: `app → feature → core/data → core/domain`; `core/domain` depends on nothing but
coroutines and datetime. Features never import each other; cross-feature navigation goes through
typed navigation keys defined in `app/shared`. Every module is a KMP library with `commonMain`,
`androidMain`, `iosMain`, `jvmMain` where needed.

```
cairn-kmp/
├── build-logic/                 # convention plugins: cairn.kmp.library, cairn.kmp.compose,
│                                # cairn.android.application, cairn.quality (spotless, detekt, kover)
├── gradle/
│   ├── libs.versions.toml       # the only place versions live
│   ├── verification-metadata.xml
│   └── gradle-daemon-jvm.properties
├── proto/                       # vendored cairn-core/api/proto at a tag (PROTO_VERSION file,
│                                # scripts/sync-proto.sh, `make proto-check` fails on drift)
├── core/
│   ├── common/                  # AppError/Result, DispatcherProvider, Clock, logging
│   ├── domain/                  # entities (Activity, Stream, Gear, Segment, …), value types
│   │                            # (Distance, Duration, Speed with unit conversion), repository
│   │                            # interfaces, use cases (one class per user intent)
│   ├── network/                 # Wire-generated cairn.v1 (commonMain), ConnectGrpcClient,
│   │                            # auth plugin, CairnApi facade per server (one stub per service)
│   ├── database/                # SQLDelight schema (.sq), driver factories (expect/actual),
│   │                            # one DB file per (server, user)
│   ├── data/                    # repository implementations: offline-first policy, mappers
│   │                            # proto/DTO → domain, pagination keys, freshness tracking
│   └── auth/                    # ServerRegistry, OAuth client registration (DCR), AuthFlow port,
│                                # TokenStore (expect/actual), SessionManager, refresh/rotation
├── ui/
│   ├── design/                  # theme (dark zinc+teal; light later), typography, components,
│   │                            # formatters (units, dates, pace), compose-resources i18n (en, de)
│   ├── map/                     # RouteMap, HeatmapMap, SegmentMap over maplibre-compose
│   └── charts/                  # StreamChart (linked cursor), LoadChart, Sparkline, EffortBars
├── feature/                     # one module per screen family, each = presentation + ui
│   ├── login/                   # server URL, OAuth login, PAT fallback
│   ├── overview/                # totals, week cards, home feed
│   ├── activities/              # list + filter bar + facets + paging
│   ├── activitydetail/          # detail, map, streams, similar, manage (read parts), photos
│   ├── heatmap/
│   ├── analysis/                # CTL/ATL/TSB
│   ├── stats/                   # stats, records, best-effort history
│   ├── segments/
│   ├── social/                  # following feed, clubs, profiles, projected + shared views
│   ├── notifications/
│   ├── gear/
│   ├── health/                  # health + athlete metrics
│   ├── connections/             # provider accounts + sync status (polling)
│   └── settings/                # prefs view (+ the few allowed writes), about, logout
├── app/
│   ├── shared/                  # Metro AppGraph, Navigation 3 host, root scaffold, deep links
│   ├── android/                 # Android application module (flavors: github, play)
│   ├── desktop/                 # JVM main, conveyor.conf
│   └── ios/                     # Xcode project with a SwiftUI shell around the shared framework
├── docs/                        # architecture.md, plan.md, adr/
├── scripts/                     # sync-proto.sh, release helpers
├── Makefile                     # task runner, same target names as cairn-core
├── .github/                     # workflows, CODEOWNERS, templates
├── .fleetlint.yaml  .pre-commit-config.yaml  renovate.json  CHANGELOG.md  SECURITY.md …
└── settings.gradle.kts
```

Phase 0 creates only `build-logic`, `core/common`, `core/domain`, `ui/design`, `app/*` and the
quality plumbing; the other modules appear in the phase that needs them.

### Layers inside a feature

- `presentation/`: `XViewModel` (constructor-injected use cases, exposes `StateFlow<XUiState>`,
  handles `XIntent`), pure Kotlin, unit-tested with Turbine.
- `ui/`: composables that take `UiState` + callbacks. No repository or network access.
- `navigation/`: the `NavKey` data classes for this feature and the `EntryProvider` registration.

### Domain model (first cut)

`Activity` (id, type, discipline, flags, title, times, timezone, `Summary`, gear, privacy,
start place/latlng, sources), `ActivityStream` (columnar arrays), `Lap`, `Zones`, `BestEffort`,
`Record`, `Segment`, `SegmentEffort`, `Gear`, `Notification`, `User` + `Preferences`,
`ExternalAccount` + `SyncStatus`, `HealthSeries`, `AthleteMetric`, `TrainingLoad`,
`OverviewSnapshot`, `StatsSnapshot`, `FeedItem`, `Club`, `Profile`. All SI; `ui/design`
formatters convert using `Preferences.units`.

## 5. Offline-first data flow

```
Composable ─observes─▶ ViewModel ─calls─▶ UseCase ─▶ Repository (interface, core/domain)
                                                          │
                                   core/data: OfflineFirstXRepository
                                   ┌──────────────┴──────────────┐
                              LocalSource                   RemoteSource
                           (SQLDelight queries           (CairnApi: Wire service
                            as Flow<…>)                   stubs over Connect)
```

- **Single source of truth is the database.** Repositories return `Flow` from SQLDelight;
  a `refresh()` fetches from the server and upserts. The UI shows cached data instantly and a
  "stale/refreshing/offline" indicator; explicit pull-to-refresh everywhere.
- **Freshness:** a `fetch_log(table_key, params_hash, fetched_at)` table; each repository has a
  TTL (activity list 5 min, detail/stream immutable until `updated_at` changes, overview/stats
  15 min, sync status 5 s while the screen is visible).
- **Storage shape:** the full proto message (Wire bytes) is stored in a blob column next to the
  indexed columns a list needs (start_time, type, discipline, gear_id, distance…).
  Streams are stored as bytes (large; evicted LRU beyond a size budget).
- **Pagination:** the opaque `PageRequest.cursor` kept in a `remote_keys` table per filter
  hash; the list screen pages through the local table.
- **Multi-account:** one SQLite file per `(server host, user id)`; switching accounts swaps the
  driver via a scoped Metro graph (`@SingleIn(AccountScope)`).
- **Errors:** `AppError` sealed class mapped from Connect codes (Network, Unauthenticated,
  PermissionDenied, NotFound, Unavailable, Internal, Unimplemented). `Unimplemented` is shown
  verbatim in debug builds so a client built ahead of a core release fails loudly, not silently.
- **Binary content** (static map PNGs, photos, GPX/TCX/FIT exports) is fetched by plain HTTPS
  GET from short-lived presigned object-store URLs that the RPCs return (handover.md, "Binary
  content"); Coil loads them without any auth header. Rules in the client: the Coil memory and
  disk cache key is the stable object identity (attachment id + etag, activity id +
  map etag), never the URL; feeds and grids request the `thumb` variant, the viewer `medium`,
  "save" the original; a URL past `expires_at` is re-requested through the RPC, not retried;
  signed URLs are never persisted in the database or logs (the cache stores bytes by key).

## 6. Authentication (industry practice for native public clients: RFC 8252)

1. User enters the server URL. The app fetches `/.well-known/oauth-authorization-server` and
   `/api/version`, stores the server in `ServerRegistry`.
2. First login on a server: dynamic client registration (`POST /oauth/register`, public client,
   `token_endpoint_auth_method: none`) with the platform's redirect URI; `client_id` cached per
   server. This is how MCP clients and other multi-server native apps do it, and core supports it
   out of the box, so no per-instance manual client setup is needed.
3. Authorization-code flow with PKCE S256 and `state` in the system browser (never a WebView):
   Custom Tabs on Android, `ASWebAuthenticationSession` on iOS, loopback redirect on desktop.
   Redirect URIs: `org.opencairn.cairn:/oauth/callback` (mobile, also registered as an app link
   later), `http://127.0.0.1:8765/callback` (desktop; core requires an exact match, so a fixed
   port, see 7).
4. Scopes requested: `activities:read profile:read social:read segments:read training:read`
   (read-only by construction; a later write-capable release adds the write scopes).
5. Tokens: access 1 h, refresh 90 d with rotation. `SessionManager` refreshes proactively, dedups
   concurrent refreshes, and on an invalid refresh token clears the session and routes to login.
   Tokens live only in `TokenStore` (Keychain / Keystore-encrypted file / 0600 file).
6. Fallback for development and headless setups: paste a personal access token.
7. Logout: `POST /oauth/revoke` for the refresh token, wipe the account's DB and token entry.

## 7. Requests to cairn-core

Everything the client needs from core beyond the RPCs that exist today is specified in
[handover.md](handover.md): h2c, the missing RPCs (grouped by client phase), signed URLs for
binary content, scope mapping per RPC, loopback redirect ports, streaming interceptors, and
publishing the protos. The client tracks core releases in the README compatibility table.

## 8. Quality gates and fleetlint

`make check` is the definition of green, mirroring cairn-core's target names:
`fmt` (spotlessApply), `lint` (spotlessCheck, detekt, buildHealth), `test` (JVM + Android unit),
`cover` (koverVerify with a floor), `audit` (osv-scanner on lockfiles, checkLicense against an
allow-list that excludes copyleft for the app's dependencies, gitleaks, zizmor),
`build` (assembleDebug + desktop packageDistributionForCurrentOS dry run + iOS framework on
macOS), `proto-check` (vendored protos match the pinned core tag), then
`fleetlint check --fail-on error`. `make mutate` (pitest) runs weekly in CI.

fleetlint: `extends: [fleetlint:recommended]`, `facts.visibility: public`, same SECURITY.md
contact and CODEOWNERS as cairn-core, curated Keep a Changelog (same override as core),
Conventional Commits via prek hooks, actions pinned to SHAs, Renovate with
`config:best-practices`, lockfile maintenance, and one group for `kotlin` + `metro` +
`compose` so compiler-plugin compatibility is bumped atomically.

## 9. Release engineering

- **Versioning:** SemVer, `CHANGELOG.md` curated by hand, `make release V=vX.Y.Z` creates a
  signed tag on main after `make check`; the tag triggers `release.yml`. 0.x until the user calls
  1.0.0. Compatibility statement: client minor N supports core v0.(N)+ as listed in the README.
- **Android:** `release.yml` builds `app-github-release.apk` with a keystore from repository
  secrets, uploads it plus `*.spdx.json` (SBOM) to the GitHub release, signs the artifacts with
  cosign keyless and attests provenance (`actions/attest-build-provenance`), like core's images.
  The Play flavor (AAB, Play App Signing) is wired after 1.0.0.
- **Desktop:** Conveyor on the Linux runner produces Linux (deb + tarball), Windows (msix + zip)
  and macOS (dmg/zip) packages and a GitHub-Releases update site; Windows self-signed and macOS
  unsigned until certificates exist (documented in the README). Auto-update on launch.
- **iOS:** `ios.yml` on `macos-latest` builds the simulator framework on PRs (compile check).
  TestFlight upload needs the Apple Developer account and certificates in secrets; planned for the
  1.0 line.
- **Supply chain:** Gradle dependency verification (checksums + PGP where available), dependency
  locking, Renovate with 3-day minimum release age, OSV alerts.

## 10. Open decisions (defaults chosen; cheap to change before Phase 1 ends)

Decided 2026-10-28: latest version of everything (JDK 25, Gradle 9.8.2), Navigation 3,
`org.opencairn.cairn`.

1. License AGPL-3.0 like cairn-core (a client is a separate program; the license is a project
   choice, not a technical one).
2. Desktop secure storage: 0600 file first, OS keyring later.
3. Decided 2026-10-28: proto RPCs only; core implements the handover (section 7).

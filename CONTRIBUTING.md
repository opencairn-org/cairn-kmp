# Contributing to cairn-kmp

Thank you for your interest in Cairn's native client. This document covers what you need to
know to file a useful issue or open a PR that has a chance of being merged.

## TL;DR

1. **Open an issue first** for non-trivial changes so we can agree on direction before you
   invest hours.
2. **Sign off your commits** with `git commit -s`. We use the
   [Developer Certificate of Origin](https://developercertificate.org); no separate CLA.
3. **Run `make check` before pushing.** It is the definition of green, and CI
   (`.github/workflows/check.yml`) runs exactly that.
4. **Keep PRs small and focused.** One change per PR.

## Development setup

Prerequisites:

- JDK 25 (Gradle provisions it through the toolchain resolver if `java` is missing)
- Android SDK with platform 37 (Android Studio installs it; set `sdk.dir` in `local.properties`)
- `make`, plus the gate tools that are not Gradle plugins: `make tools` (osv-scanner,
  fleetlint, prek, jscpd); [gitleaks](https://github.com/gitleaks/gitleaks) comes from its
  own release
- Git hooks: `prek install` (runs `.pre-commit-config.yaml`; `make check-fast` on push)
- For iOS: a Mac with Xcode; the Kotlin framework is built by Gradle, the app shell lives in
  `app/ios`

Quick start:

```sh
git clone https://github.com/opencairn-org/cairn-kmp
cd cairn-kmp
make build          # debug APK + desktop jar
make desktop        # run the desktop app against your cairn-core
```

## Testing against a local cairn-core

1. In the cairn-core checkout: `podman compose -f docker-compose.dev.yml up -d`, build the server
   with the web UI embedded (`make web-build`, copy `web/build` to
   `internal/adapter/primary/web/dist`, `go build -tags embedweb ./cmd/server`) and run it with
   `dev.env.example` values plus `CAIRN_HTTP_PUBLIC_BASE_URL=http://localhost:8080` and
   `CAIRN_API_ORIGIN=http://localhost:8080`.
2. Seed data: sign in as the bootstrap admin (`POST /auth/password`), create a PAT
   (`POST /api/pats`), upload GPX files (`POST /api/activities/upload`) or manual activities
   (`POST /api/activities/manual`).
3. Headless: `make e2e CAIRN_E2E_BASE_URL=http://localhost:8080 CAIRN_E2E_TOKEN=cairn_pat_…`
   runs the real app graph (sign-in, overview, search, detail, cache) against it.
4. Emulator: `adb reverse tcp:8080 tcp:8080` (and `tcp:9000` for MinIO content), install the
   debug APK (cleartext to localhost is allowed in debug builds only) and enter
   `http://localhost:8080` as the server. Both the browser sign-in (Custom Tab, core's consent
   page, redirect back by custom scheme) and the token sign-in work this way.

## Architecture

Read `docs/ARCHITECTURE.md` and the ADRs in `docs/adr/` first. The short version:

- `core/domain` is pure Kotlin: entities, repository interfaces, use cases. No Compose, no I/O.
- `core/data` implements repositories offline-first: SQLDelight is the source of truth,
  the network refreshes it.
- `core/network` is the proto RPC client only (Wire-generated stubs over the Connect
  protocol). There is no REST client in this app.
- `feature/*` modules own a screen family: ViewModel + composables + navigation keys. Features
  never import each other.
- `app/*` wires the Metro graph and the platform entry points.

## Changelog and releases

Every user-visible change gets a line under `[Unreleased]` in `CHANGELOG.md` (Keep a
Changelog). Releases are signed tags created by `make release V=vX.Y.Z`; the tag triggers the
release workflow that builds, signs and publishes the artifacts.

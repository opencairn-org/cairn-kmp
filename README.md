# cairn-kmp

The native client for [Cairn](https://github.com/opencairn-org/cairn-core): your activities,
on Android, iOS, Linux, Windows and macOS, from one Kotlin Multiplatform code base.
View-only for now; imports and edits stay in the web UI until after 1.0.

Status: pre-1.0, under construction. Nothing is released yet.

## Install

Releases are published on the [GitHub releases page](https://github.com/opencairn-org/cairn-kmp/releases):

- **Android:** `cairn-<version>-android.apk`, signed; the app checks that page for updates.
  A Play Store listing follows after 1.0.0.
- **Desktop:** Linux (`.deb`, tarball), Windows (`.msix`, zip) and macOS (`.dmg`) packages
  built with Conveyor, with automatic updates.
- **iOS:** TestFlight, once Apple signing is set up.

Every artifact is signed keyless with cosign and carries a provenance attestation; the release
notes list the `cosign verify-blob` and `gh attestation verify` commands.

## Compatibility

| cairn-kmp | cairn-core |
|---|---|
| 0.1.x | the release that ships handover group H2 (see `docs/handover.md`) |

## Quickstart

Requirements: JDK 25 (Gradle provisions it if missing), the Android SDK with platform 37,
`make`, and the gate tools from `make tools`.

```sh
make build        # debug APK (GitHub flavor) + desktop jar
make desktop      # run the desktop app
make check        # the definition of green: lint, tests, coverage, audit, build, fleetlint
make help         # every target
```

Point the app at a cairn-core instance on first start; it registers itself with the server's
OAuth 2.1 authorization server and signs you in through your browser.

## Architecture

`docs/ARCHITECTURE.md` is the one-page overview (modules, offline-first data flow, auth,
release engineering); `docs/adr/` records the decisions; `docs/plan.md` is the phased plan;
`docs/handover.md` is what cairn-core implements for this client. The short version: proto RPCs
only, SQLDelight as the source of truth, Compose Multiplatform UI, Metro for DI.

## Contributing

See `CONTRIBUTING.md`. Security reports: `SECURITY.md`.

## License

AGPL-3.0, like cairn-core. See `LICENSE`.

# Releasing

Releases are signed tags on `main`; the tag triggers `.github/workflows/release.yml`, which
gates on `check.yml`, builds the artifacts, signs them keyless with cosign, attests provenance,
and creates the GitHub release with the tag's `CHANGELOG.md` section. Releases are immutable.

## One-time setup (repository settings)

1. **Environment `release`** with required reviewers (the release jobs run in it and wait for
   approval, like the other cairn repositories).
2. **Android upload keystore** (generated once, never committed):
   ```sh
   keytool -genkeypair -v -keystore cairn-upload.jks -alias cairn -keyalg RSA -keysize 4096 \
     -validity 10000 -dname "CN=Cairn, O=The Cairn Authors"
   base64 -w0 cairn-upload.jks > cairn-upload.jks.b64
   ```
   Environment secrets: `ANDROID_KEYSTORE_BASE64` (the `.b64` content),
   `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`cairn`), `ANDROID_KEY_PASSWORD`.
   Keep the keystore in the password manager; losing it means a new application id for the
   GitHub channel. The Play channel later uses Play App Signing with its own upload key.
3. **Conveyor signing key**: `conveyor keys generate` once; the root key goes into the
   `CONVEYOR_SIGNING_KEY` environment secret. Windows and macOS packages are self-signed /
   unsigned until certificates exist; Linux packages need no certificate.
4. **Rulesets** as in cairn-core: `main` requires the `check` status, `v*` tags are immutable
   and admin-only.

## Cutting a release

```sh
# on main, clean tree, CHANGELOG.md has a "## [X.Y.Z] - YYYY-MM-DD" section
make release V=vX.Y.Z      # runs make check, creates the signed tag
git push origin vX.Y.Z     # triggers release.yml; approve the `release` environment in Actions
```

`versionCode` is derived from the tag (`major*1_000_000 + minor*1_000 + patch`), so every
release has a higher code than the one before; `versionName` is the tag without the `v`.
Tags with a pre-release suffix (`v0.4.0-rc.1`) become GitHub pre-releases.

## What a release contains

| Artifact | Built by | Signed |
|---|---|---|
| `cairn-<v>-android.apk` | `assembleGithubRelease` (R8, resource shrinking) | upload keystore + cosign bundle + provenance attestation |
| `cairn-<v>-android.cdx.json` | CycloneDX Gradle plugin | cosign bundle |
| Linux `.deb`/tarball, Windows `.msix`/zip, macOS `.zip` (Apple Silicon) | Conveyor | Conveyor (self-signed until certificates exist) + provenance |
| `cairn-<v>.spdx.json` | syft over the source tree | — |
| `checksums.txt` | `sha256sum` | — |

Verify commands are appended to every release's notes.

## Verifying locally before tagging

```sh
make build-release                      # unsigned release APK with R8; check it still launches
unzip -l app/android/build/outputs/apk/github/release/*.apk | grep classes   # dex size sanity
./gradlew :app:desktop:printConveyorConfig   # Conveyor sees the right inputs and version
```

## Android channels

- **GitHub** (`github` flavor, `BuildConfig.DISTRIBUTION = "github"`): the APK on the release
  page; the app checks the releases API for updates (planned).
- **Play** (`play` flavor): AAB with Play App Signing, after 1.0.0. Same application id, so
  users switching channels keep their data.

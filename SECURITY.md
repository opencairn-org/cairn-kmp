# Security policy

## Supported versions

The latest release (the newest `v*` tag and the APK / desktop packages attached to its GitHub
release). Older releases do not receive fixes.

## Reporting a vulnerability

Please do not open a public issue. Report suspected vulnerabilities privately through GitHub's
"Report a vulnerability" button on the Security tab, or by email to `security@johanneskueber.com`.
You will get an acknowledgement within 7 days and a fix or a decision within 30.

Vulnerabilities in the server belong to [cairn-core](https://github.com/opencairn-org/cairn-core);
vulnerabilities in the provider workers belong to their own repositories.

## Verifying releases

Release artifacts are signed keyless with cosign and carry SBOM and provenance attestations; the
GitHub release notes list the `cosign verify-blob` and `gh attestation verify` commands.

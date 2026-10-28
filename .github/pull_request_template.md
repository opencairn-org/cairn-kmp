## What and why

## Checklist

- [ ] `make check` is green locally (format, lint, tests, coverage, audit, build, fleetlint)
- [ ] New behaviour has tests; `CHANGELOG.md` has an entry under `[Unreleased]`
- [ ] Schema changes are a new SQLDelight migration (never an edit to an existing one)
- [ ] Proto changes come from cairn-core via `make proto-sync` (`PROTO_VERSION` updated)
- [ ] Commits are signed off (`git commit -s`)

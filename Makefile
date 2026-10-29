# Task runner for cairn-kmp. `make check` is the definition of green and what CI runs.
SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help

GRADLE ?= ./gradlew
GRADLE_FLAGS ?= --console=plain
COVER_MIN ?= 40
# renovate: datasource=github-releases depName=google/osv-scanner
OSV_SCANNER_VERSION ?= v2.7.0
# renovate: datasource=github-releases depName=fleetlint/fleetlint
FLEETLINT_VERSION ?= v0.9.0
PROTO_VERSION := $(shell cat PROTO_VERSION 2>/dev/null)

.PHONY: help
help: ## list targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

.PHONY: tools
# renovate: datasource=pypi depName=prek
PREK_VERSION ?= 0.5.7
# renovate: datasource=npm depName=jscpd
JSCPD_VERSION ?= 5.4.2

tools: ## install the pinned gate tools that are not Gradle plugins (osv-scanner, fleetlint, prek, jscpd)
	go install github.com/google/osv-scanner/v2/cmd/osv-scanner@$(OSV_SCANNER_VERSION)
	go install github.com/fleetlint/fleetlint/cmd/fleetlint@$(FLEETLINT_VERSION)
	uv tool install prek==$(PREK_VERSION)
	npm install -g jscpd@$(JSCPD_VERSION)

.PHONY: fmt
fmt: ## format in place (Spotless: ktlint for Kotlin, build scripts, config files)
	$(GRADLE) $(GRADLE_FLAGS) spotlessApply

.PHONY: lint
lint: ## format check + detekt + dependency health, warnings as errors
	$(GRADLE) $(GRADLE_FLAGS) spotlessCheck detekt buildHealth
	jscpd --config config/jscpd.json --ignore-pattern '^\s*import .*$$'

.PHONY: test
test: ## unit tests on the JVM targets (common code runs there)
	$(GRADLE) $(GRADLE_FLAGS) allTests

.PHONY: cover
cover: ## coverage gate: Kover total line coverage >= COVER_MIN
	$(GRADLE) $(GRADLE_FLAGS) koverVerify koverXmlReport -Pcairn.coverMin=$(COVER_MIN)

.PHONY: audit
audit: licenses ## vulnerabilities (osv-scanner on the Gradle lockfiles), licenses, secrets
	osv-scanner scan source --config config/osv-scanner.toml --recursive .
	gitleaks git --no-banner --redact

.PHONY: licenses
licenses: ## third-party license report + allow-list gate (checkLicense)
	$(GRADLE) $(GRADLE_FLAGS) generateLicenseReport checkLicense

.PHONY: build
build: ## debug APK for the GitHub flavor + desktop jar (release packaging is in CI)
	$(GRADLE) $(GRADLE_FLAGS) :app:android:assembleGithubDebug :app:desktop:jar

.PHONY: build-release
build-release: ## unsigned release APK (CI signs with CAIRN_KEYSTORE_* in the environment)
	$(GRADLE) $(GRADLE_FLAGS) :app:android:assembleGithubRelease

.PHONY: e2e
e2e: ## end-to-end against a running cairn-core: make e2e CAIRN_E2E_BASE_URL=http://localhost:8080 CAIRN_E2E_TOKEN=cairn_pat_…
	CAIRN_E2E_BASE_URL="$(CAIRN_E2E_BASE_URL)" CAIRN_E2E_TOKEN="$(CAIRN_E2E_TOKEN)" $(GRADLE) $(GRADLE_FLAGS) :app:desktop:test --rerun -i 2>&1 | grep -E "^(server |signed in|overview|home feed|rides|detail|skipped)|BUILD|FAILED|Exception" | head -40

.PHONY: desktop
desktop: ## run the desktop app
	$(GRADLE) $(GRADLE_FLAGS) :app:desktop:run

.PHONY: proto-check
proto-check: ## vendored protos match cairn-core at PROTO_VERSION
	scripts/sync-proto.sh --check "$(PROTO_VERSION)"

.PHONY: proto-sync
proto-sync: ## re-vendor protos from cairn-core: make proto-sync V=vX.Y.Z
	scripts/sync-proto.sh "$(V)"

.PHONY: update-locks
update-locks: ## rewrite the Gradle dependency lockfiles (never in CI)
	$(GRADLE) $(GRADLE_FLAGS) --no-configuration-cache resolveAndLockAll --write-locks

.PHONY: mutate
mutate: ## mutation testing of core/* on the JVM (weekly in CI, not part of check)
	$(GRADLE) $(GRADLE_FLAGS) pitest

.PHONY: sbom
sbom: ## CycloneDX SBOM of the Android app's runtime dependencies
	$(GRADLE) $(GRADLE_FLAGS) :app:android:cyclonedxBom

.PHONY: check-fast
check-fast: ## before every commit (< 1 min): format check + detekt + JVM unit tests
	$(GRADLE) $(GRADLE_FLAGS) spotlessCheck detekt desktopTest

.PHONY: check
check: lint test cover audit build ## definition of green (< 10 min)
	fleetlint check --fail-on error

.PHONY: release
release: ## refuse dirty/non-main -> check -> signed tag V (its CHANGELOG.md section must exist); pushing the tag publishes
	@test -n "$(V)" || { echo "usage: make release V=vX.Y.Z"; exit 2; }
	@test -z "$$(git status --porcelain)" || { echo "working tree is dirty"; exit 1; }
	@test "$$(git branch --show-current)" = "main" || { echo "not on main"; exit 1; }
	@grep -q "^## \[$(V:v%=%)\]" CHANGELOG.md || { echo "CHANGELOG.md has no section for $(V)"; exit 1; }
	$(MAKE) check
	git tag -s "$(V)" -m "$(V)"
	@echo "tag $(V) created; push it with: git push origin $(V)"

.PHONY: clean
clean: ## remove build output
	$(GRADLE) $(GRADLE_FLAGS) clean

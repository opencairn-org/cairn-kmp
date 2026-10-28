#!/usr/bin/env bash
# Vendor cairn-core's proto contract at a tag into proto/ and record it in PROTO_VERSION.
#   scripts/sync-proto.sh vX.Y.Z            copy the protos of that tag
#   scripts/sync-proto.sh --check vX.Y.Z    fail if proto/ differs from that tag
set -euo pipefail

repo="${CAIRN_CORE_REPO:-https://github.com/opencairn-org/cairn-core.git}"
check=0
if [[ "${1:-}" == "--check" ]]; then check=1; shift; fi
tag="${1:-}"
[[ -n "$tag" ]] || { echo "usage: $0 [--check] vX.Y.Z" >&2; exit 2; }

root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
git clone --quiet --depth 1 --branch "$tag" "$repo" "$tmp/core"
src="$tmp/core/api/proto"

if (( check )); then
  if diff -r --exclude=buf.gen.yaml --exclude=buf.gen.local.yaml "$src" "$root/proto" >/dev/null \
     && [[ "$(cat "$root/PROTO_VERSION")" == "$tag" ]]; then
    echo "proto/ matches cairn-core $tag"
  else
    echo "proto/ differs from cairn-core $tag; run: make proto-sync V=$tag" >&2
    diff -r --exclude=buf.gen.yaml --exclude=buf.gen.local.yaml "$src" "$root/proto" || true
    exit 1
  fi
  exit 0
fi

rm -rf "$root/proto"
mkdir -p "$root/proto"
cp -R "$src"/. "$root/proto/"
rm -f "$root/proto/buf.gen.yaml" "$root/proto/buf.gen.local.yaml"
echo "$tag" > "$root/PROTO_VERSION"
echo "vendored cairn-core $tag protos into proto/"

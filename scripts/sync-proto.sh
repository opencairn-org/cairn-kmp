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
  # Files carrying `DRAFT(handover` blocks are the client's draft of RPCs cairn-core has not
  # shipped yet (docs/handover.md); they are reported, not diffed. Everything else must match.
  status=0
  [[ "$(cat "$root/PROTO_VERSION")" == "$tag" ]] || { echo "PROTO_VERSION is not $tag" >&2; status=1; }
  while IFS= read -r -d '' f; do
    rel="${f#"$root/proto/"}"
    if grep -q 'DRAFT(handover' "$f"; then
      echo "draft: $rel (contains handover drafts, not compared)"
      continue
    fi
    if ! diff -q "$src/$rel" "$f" >/dev/null 2>&1; then
      echo "proto/$rel differs from cairn-core $tag; run: make proto-sync V=$tag" >&2
      diff "$src/$rel" "$f" || true
      status=1
    fi
  done < <(find "$root/proto" -name '*.proto' -print0)
  (( status == 0 )) && echo "proto/ matches cairn-core $tag (drafts excepted)"
  exit $status
fi

rm -rf "$root/proto"
mkdir -p "$root/proto"
cp -R "$src"/. "$root/proto/"
rm -f "$root/proto/buf.gen.yaml" "$root/proto/buf.gen.local.yaml"
echo "$tag" > "$root/PROTO_VERSION"
echo "vendored cairn-core $tag protos into proto/"

#!/usr/bin/env bash
set -euo pipefail
image="$1"
failure_file="$(mktemp)"
trap 'rm -f "$failure_file"' EXIT
for attempt in 1 2 3; do
  if docker buildx imagetools inspect "$image" >/dev/null 2>"$failure_file"; then
    echo "Reusing immutable image $image"
    exit 0
  fi
  if grep -Eqi 'manifest unknown|name unknown|not found|404 Not Found' "$failure_file"; then
    exit 1
  fi
  sleep 2
done
cat "$failure_file" >&2
exit 2

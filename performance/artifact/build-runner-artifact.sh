#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
output="${1:-$root/build/runner-artifact/runner.tar.gz}"
revision="${APP_REVISION:-$(git -C "$root" rev-parse HEAD)}"
staging="$(mktemp -d)"
cleanup() { rm -rf "$staging"; }
trap cleanup EXIT

mkdir -p "$(dirname "$output")"
docker build \
  --quiet \
  --build-arg "APP_REVISION=$revision" \
  --file "$root/performance/artifact/Dockerfile" \
  --target artifact \
  --output "type=tar,dest=$staging/artifact.tar" \
  "$root"

mkdir -p "$staging/root"
tar --extract --no-same-owner --no-same-permissions --file "$staging/artifact.tar" --directory "$staging/root"
tar --create --gzip --file "$output" \
  --directory "$staging/root" \
  --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner .
printf 'Created %s (Backend revision: %s)\n' "$output" "$revision"

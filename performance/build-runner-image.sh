#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || -z "$1" ]]; then
  echo "Usage: $0 <image-ref>" >&2
  echo "Example: $0 <account>.dkr.ecr.<region>.amazonaws.com/<repository>:<backend-git-sha>" >&2
  exit 2
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
image_ref="$1"
revision="${APP_REVISION:-$(git -C "$root" rev-parse HEAD)}"

docker build \
  --build-arg "APP_REVISION=$revision" \
  --file "$root/performance/Dockerfile" \
  --tag "$image_ref" \
  "$root"

printf 'Built %s (Backend revision: %s)\n' "$image_ref" "$revision"

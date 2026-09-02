#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || -z "$1" ]]; then
  echo "Usage: $0 <repository-uri>" >&2
  echo "Example: $0 <account>.dkr.ecr.<region>.amazonaws.com/<repository>" >&2
  exit 2
fi

repository="$1"
if [[ "$repository" == */ || "$repository" == *@* || "${repository##*/}" == *:* ]]; then
  echo "Repository URI must not include a tag or digest: $repository" >&2
  exit 2
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
revision="$(git -C "$root" rev-parse HEAD)"
image_ref="${repository}:${revision}"

docker build \
  --build-arg "APP_REVISION=$revision" \
  --file "$root/performance/Dockerfile" \
  --tag "$image_ref" \
  "$root"

printf 'Built %s (Backend revision: %s)\n' "$image_ref" "$revision"

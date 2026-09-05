#!/usr/bin/env bash
set -euo pipefail

: "${AWS_REGION:?AWS_REGION is required}"
: "${RUNNER_ECR_REPOSITORY:?RUNNER_ECR_REPOSITORY is required}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
revision="$(git -C "$root" rev-parse HEAD)"
[[ "$revision" =~ ^[0-9a-f]{40}$ ]]

repository_json="$(aws ecr describe-repositories --region "$AWS_REGION" \
  --repository-names "$RUNNER_ECR_REPOSITORY" --output json)"
mutability="$(jq -r '.repositories[0].imageTagMutability' <<< "$repository_json")"
test "$mutability" = IMMUTABLE
repository_uri="$(jq -er '.repositories[0].repositoryUri' <<< "$repository_json")"
image_uri="${repository_uri}:${revision}"

# batch-get-image distinguishes a missing tag from permission/network failures.
existing="$(aws ecr batch-get-image --region "$AWS_REGION" \
  --repository-name "$RUNNER_ECR_REPOSITORY" --image-ids "imageTag=$revision" --output json)"
if jq -e '.images | length == 0' <<< "$existing" >/dev/null; then
  jq -e '.failures | length == 1 and .[0].failureCode == "ImageNotFound"' <<< "$existing" >/dev/null
  "$root/performance/build-runner-image.sh" "$repository_uri"
  docker run --rm --network none --entrypoint /workspace/bin/verify-runtime "$image_uri"
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "${repository_uri%%/*}"
  "$root/bin/publish-runner-image" "$image_uri"
else
  jq -e '(.images | length == 1) and (.failures | length == 0)' <<< "$existing" >/dev/null
  echo "Reusing immutable Runner image $image_uri"
fi

digest="$(aws ecr describe-images --region "$AWS_REGION" \
  --repository-name "$RUNNER_ECR_REPOSITORY" --image-ids "imageTag=$revision" \
  --query 'imageDetails[0].imageDigest' --output text)"
[[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]
jq -n --arg source_commit_sha "$revision" --arg image_tag "$revision" \
  --arg image_digest "$digest" --arg image_uri "$image_uri" \
  --arg image_digest_uri "${repository_uri}@${digest}" \
  '{source_commit_sha: $source_commit_sha, image_tag: $image_tag,
    image_digest: $image_digest, image_uri: $image_uri, image_digest_uri: $image_digest_uri}' \
  > "$root/runner-image.json"
cat "$root/runner-image.json"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  printf 'image_tag=%s\nimage_digest=%s\nsource_commit_sha=%s\n' \
    "$revision" "$digest" "$revision" >> "$GITHUB_OUTPUT"
fi
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo '### Performance Runner image'
    printf '\n- Source commit SHA / APP_REVISION: `%s`\n' "$revision"
    printf -- '- Image tag: `%s`\n- Image digest: `%s`\n' "$revision" "$digest"
    printf -- '- Image URI: `%s`\n- IaC digest URI: `%s@%s`\n' "$image_uri" "$repository_uri" "$digest"
  } >> "$GITHUB_STEP_SUMMARY"
fi

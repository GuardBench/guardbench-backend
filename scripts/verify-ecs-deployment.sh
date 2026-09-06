#!/usr/bin/env bash

set -euo pipefail

: "${ECS_CLUSTER:?ECS_CLUSTER is required}"
: "${ECS_SERVICE:?ECS_SERVICE is required}"
: "${TARGET_TASK_DEFINITION:?TARGET_TASK_DEFINITION is required}"

max_attempts="${ECS_DEPLOYMENT_VERIFY_ATTEMPTS:-12}"
interval_seconds="${ECS_DEPLOYMENT_VERIFY_INTERVAL_SECONDS:-5}"

if ! [[ "$max_attempts" =~ ^[1-9][0-9]*$ ]]; then
  echo "ECS_DEPLOYMENT_VERIFY_ATTEMPTS must be a positive integer." >&2
  exit 2
fi
if ! [[ "$interval_seconds" =~ ^[0-9]+$ ]]; then
  echo "ECS_DEPLOYMENT_VERIFY_INTERVAL_SECONDS must be a non-negative integer." >&2
  exit 2
fi

print_state() {
  local state="$1"
  jq '{
    taskDefinition,
    desiredCount,
    runningCount,
    pendingCount,
    deployments: [.deployments[] | {
      status,
      taskDefinition,
      desiredCount,
      runningCount,
      pendingCount,
      failedTasks,
      rolloutState,
      rolloutStateReason
    }],
    events: .events[0:10]
  }' <<< "$state"
}

for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  service_state="$(aws ecs describe-services \
    --cluster "$ECS_CLUSTER" \
    --services "$ECS_SERVICE" \
    --query 'services[0]' \
    --output json)"

  if [[ -z "$service_state" || "$service_state" == "null" ]]; then
    echo "ECS service was not found while verifying ${TARGET_TASK_DEFINITION}." >&2
    exit 1
  fi

  actual_task_definition="$(jq -r '.taskDefinition // empty' <<< "$service_state")"
  primary_rollout_state="$(jq -r '
    [.deployments[] | select(.status == "PRIMARY") | .rolloutState] | first // empty
  ' <<< "$service_state")"
  primary_rollout_reason="$(jq -r '
    [.deployments[] | select(.status == "PRIMARY") | (.rolloutStateReason // "")] | first // ""
  ' <<< "$service_state")"
  desired_count="$(jq -r '.desiredCount // -1' <<< "$service_state")"
  running_count="$(jq -r '.runningCount // -1' <<< "$service_state")"
  pending_count="$(jq -r '.pendingCount // -1' <<< "$service_state")"
  primary_failed_tasks="$(jq -r '
    [.deployments[] | select(.status == "PRIMARY") | (.failedTasks // 0)] | first // 0
  ' <<< "$service_state")"

  echo "ECS deployment verification ${attempt}/${max_attempts}: taskDefinition=${actual_task_definition:-<none>} primaryRollout=${primary_rollout_state:-<none>} desired=${desired_count} running=${running_count} pending=${pending_count} failed=${primary_failed_tasks}"

  if [[ "$primary_rollout_state" == "FAILED" ]] || grep -Eiq 'rollback|rolled back' <<< "$primary_rollout_reason"; then
    echo "ECS deployment entered a failed or rollback state while deploying ${TARGET_TASK_DEFINITION}." >&2
    print_state "$service_state" >&2
    exit 1
  fi

  if [[ "$actual_task_definition" == "$TARGET_TASK_DEFINITION" \
    && "$primary_rollout_state" == "COMPLETED" \
    && "$desired_count" == "$running_count" \
    && "$pending_count" == "0" \
    && "$primary_failed_tasks" == "0" ]]; then
    print_state "$service_state"
    exit 0
  fi

  if (( attempt < max_attempts )); then
    sleep "$interval_seconds"
  fi
done

echo "ECS deployment did not converge to ${TARGET_TASK_DEFINITION} within ${max_attempts} checks." >&2
print_state "$service_state" >&2
exit 1

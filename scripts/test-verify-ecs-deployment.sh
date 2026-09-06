#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

mkdir -p "$tmp_dir/bin" "$tmp_dir/sequence"
count_file="$tmp_dir/count"
printf '0\n' > "$count_file"

cat > "$tmp_dir/bin/aws" <<'MOCK_AWS'
#!/usr/bin/env bash
set -euo pipefail

if [[ "$1" != "ecs" || "$2" != "describe-services" ]]; then
  echo "unexpected aws invocation: $*" >&2
  exit 99
fi

count_file="${AWS_TEST_COUNT_FILE:?}"
sequence_dir="${AWS_TEST_SEQUENCE_DIR:?}"
count="$(<"$count_file")"
count=$((count + 1))
printf '%s\n' "$count" > "$count_file"

if [[ -f "$sequence_dir/$count.json" ]]; then
  cat "$sequence_dir/$count.json"
else
  cat "$sequence_dir/final.json"
fi
MOCK_AWS
chmod +x "$tmp_dir/bin/aws"

cat > "$tmp_dir/bin/jq" <<'MOCK_JQ'
#!/usr/bin/env python3
import json
import sys

expression = next((arg for arg in sys.argv[1:] if not arg.startswith('-') and arg not in {'--arg'}), '')
data = json.load(sys.stdin)
deployments = [item for item in data.get('deployments', []) if item.get('status') == 'PRIMARY']

if expression.lstrip().startswith('{'):
    json.dump(data, sys.stdout)
    sys.stdout.write('\n')
elif 'rolloutStateReason' in expression:
    print((deployments[0].get('rolloutStateReason') if deployments else '') or '')
elif 'rolloutState' in expression:
    print((deployments[0].get('rolloutState') if deployments else '') or '')
elif '.taskDefinition' in expression:
    print(data.get('taskDefinition', '') or '')
elif '.desiredCount' in expression:
    print(data.get('desiredCount', -1))
elif '.runningCount' in expression:
    print(data.get('runningCount', -1))
elif '.pendingCount' in expression:
    print(data.get('pendingCount', -1))
elif 'failedTasks' in expression:
    print((deployments[0].get('failedTasks', 0) if deployments else 0) or 0)
else:
    raise SystemExit(f'unexpected jq expression: {expression}')
MOCK_JQ
chmod +x "$tmp_dir/bin/jq"

cat > "$tmp_dir/bin/sleep" <<'MOCK_SLEEP'
#!/usr/bin/env bash
exit 0
MOCK_SLEEP
chmod +x "$tmp_dir/bin/sleep"

target="arn:aws:ecs:ap-northeast-2:123456789012:task-definition/guardbench-dev-app:42"

cat > "$tmp_dir/sequence/1.json" <<'STATE_IN_PROGRESS'
{
  "taskDefinition": "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/guardbench-dev-app:41",
  "desiredCount": 1,
  "runningCount": 1,
  "pendingCount": 0,
  "deployments": [{
    "status": "PRIMARY",
    "taskDefinition": "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/guardbench-dev-app:42",
    "desiredCount": 1,
    "runningCount": 1,
    "pendingCount": 0,
    "failedTasks": 0,
    "rolloutState": "IN_PROGRESS",
    "rolloutStateReason": "ECS deployment in progress."
  }],
  "events": []
}
STATE_IN_PROGRESS

cat > "$tmp_dir/sequence/2.json" <<STATE_COMPLETED
{
  "taskDefinition": "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/guardbench-dev-app:42",
  "desiredCount": 1,
  "runningCount": 1,
  "pendingCount": 0,
  "deployments": [{
    "status": "PRIMARY",
    "taskDefinition": "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/guardbench-dev-app:42",
    "desiredCount": 1,
    "runningCount": 1,
    "pendingCount": 0,
    "failedTasks": 0,
    "rolloutState": "COMPLETED",
    "rolloutStateReason": "ECS deployment completed."
  }],
  "events": []
}
STATE_COMPLETED
cp "$tmp_dir/sequence/2.json" "$tmp_dir/sequence/final.json"

convergence_output="$(
  PATH="$tmp_dir/bin:$PATH" \
  AWS_TEST_COUNT_FILE="$count_file" \
  AWS_TEST_SEQUENCE_DIR="$tmp_dir/sequence" \
  ECS_CLUSTER=cluster \
  ECS_SERVICE=service \
  TARGET_TASK_DEFINITION="$target" \
  ECS_DEPLOYMENT_VERIFY_INTERVAL_SECONDS=0 \
  bash "$script_dir/verify-ecs-deployment.sh" 2>&1
)"

grep -q 'verification 1/12.*primaryRollout=IN_PROGRESS' <<< "$convergence_output"
grep -q 'verification 2/12.*primaryRollout=COMPLETED' <<< "$convergence_output"
[[ "$(<"$count_file")" == "2" ]]

cat > "$tmp_dir/sequence/1.json" <<'STATE_FAILED'
{
  "taskDefinition": "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/guardbench-dev-app:41",
  "desiredCount": 1,
  "runningCount": 0,
  "pendingCount": 0,
  "deployments": [{
    "status": "PRIMARY",
    "taskDefinition": "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/guardbench-dev-app:41",
    "desiredCount": 1,
    "runningCount": 0,
    "pendingCount": 0,
    "failedTasks": 1,
    "rolloutState": "FAILED",
    "rolloutStateReason": "Deployment rolled back."
  }],
  "events": []
}
STATE_FAILED
printf '0\n' > "$count_file"

if PATH="$tmp_dir/bin:$PATH" \
  AWS_TEST_COUNT_FILE="$count_file" \
  AWS_TEST_SEQUENCE_DIR="$tmp_dir/sequence" \
  ECS_CLUSTER=cluster \
  ECS_SERVICE=service \
  TARGET_TASK_DEFINITION="$target" \
  ECS_DEPLOYMENT_VERIFY_INTERVAL_SECONDS=0 \
  bash "$script_dir/verify-ecs-deployment.sh" > "$tmp_dir/failure.log" 2>&1; then
  echo 'expected FAILED rollout to be rejected' >&2
  exit 1
fi

grep -q 'failed or rollback state' "$tmp_dir/failure.log"
echo 'verify-ecs-deployment tests passed'

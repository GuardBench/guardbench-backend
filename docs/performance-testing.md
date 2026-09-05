# 성능 테스트 운영

> Status: APPROVED
> Owner: Backend
> Related Issue: #140, #187, #193, #200, #212

## 목적과 원칙

GuardBench 성능 테스트는 현재 ECS 설정값을 목표로 삼지 않고, Capacity Target을 먼저 정한
뒤 동일한 workload를 반복 실행해 Baseline과 인프라 변경 전후를 비교하기 위한 도구다.
TestRun은 `POST /api/v1/test-runs`의 `202 Accepted`에서 끝나지 않고 SQS/Worker를 거쳐
`FINISHED`가 되므로 HTTP latency와 비동기 완료 시간을 분리해서 측정한다.

현재 E2E 실행 경로는 다음과 같다.

```text
TestRun → controlled Application Target (Demo AI)
        → SageMaker Response Behavior Classifier
        → ALLOW/BLOCK 정규화 → Assertion → Metrics / Quality Gate
```

여기서 Demo AI는 GuardBench 자체의 성능 테스트 대상(SUT)이 아니다. 실제 고객의
OpenAI-compatible Application Target을 대신하는 고정된 synthetic target이며, 외부 고객
서비스의 latency/throughput 변동이 GuardBench 성능 측정에 섞이지 않도록 통제하는 fixture다.
따라서 Demo AI 자체의 최대 throughput, CPU saturation, scaling 특성을 측정하지 않으며,
`demo_ai_cpu`, `demo_ai_memory`, `demo_ai_desired_count`를 MVP capacity sweep 축으로 사용하지
않는다. Demo AI의 application code에 artificial delay/throttling을 추가하거나 latency를
임의로 하드코딩하지 않는다.

비교 가능한 실험에서는 Demo AI image/revision, model/config, ECS CPU/memory, desired count,
Application Target endpoint와 가능한 한 동일한 latency 특성을 고정한다. 이 조건이 변경된
실행은 GuardBench capacity 변경 전후의 동일 조건 실험으로 간주하지 않는다. Profile의
Application Target은 workload가 호출할 target을 지정할 뿐이며, target의 capacity를
GuardBench 성능 실험 축으로 소유하지 않는다.

성능 Profile은 Application Target과 workload/acceptance criteria만 입력으로 소유한다.
`evaluationProfile`, `checks`, `strictness` 같은 evaluator 설정은 TestRun 생성 계약이 아니며,
classifier endpoint/prompt 등은 Backend 배포 설정이 소유한다.

실험 순서는 다음과 같다.

```text
Capacity Target → Performance Profile → 동일 Dataset 실행 → Baseline
→ 병목 분석 → 인프라 변경 → 동일 workload 재검증
```

현재 단일 ECS Service는 최초 Baseline 측정 대상일 뿐 성능 목표의 근거가 아니다. API/Worker
분리, Worker Auto Scaling, Fargate 자원 조정 이후에도 같은 Dataset과 Profile을 사용한다.

## Infrastructure Capacity 입력과 환경 분리

ECS task CPU/memory는 Profile이나 Workload가 아니라 Terraform/배포 설정이 소유하는
Infrastructure Capacity 입력이다. IaC의 Performance Backend는 dev와 분리된 CPU/memory 입력
(예: `performance_api_cpu`, `performance_api_memory`)을 사용하고, dev Backend는 `api_cpu`와
`api_memory`를 계속 사용한다.
따라서 Performance task size를 변경해도 dev Task Definition이나 Service가 변경 대상이
되지 않아야 한다. 실제 변수와 Task Definition 연결은 [guardbench-iac Issue #41](https://github.com/GuardBench/guardbench-iac/issues/41)의
계약을 따른다.

MVP에서 Capacity 실험으로 변경하는 대상은 Performance Backend ECS와 SageMaker classifier다.
RDS와 SQS는 Performance 전용 실행을 지지하는 고정·관찰 대상이며, capacity tuning 축으로
취급하지 않는다.

| 구분 | 대상 | MVP 성능 테스트에서의 역할 |
| --- | --- | --- |
| 주요 Capacity 실험 대상 | Performance Backend ECS | task CPU/memory 등 configured capacity를 변경·비교 |
| 주요 Capacity 실험 대상 | SageMaker classifier | classifier의 configured capacity를 변경·비교 |
| 고정·관찰 대상 | Performance 전용 RDS | 실행 기간 동안 고정하고 병목 여부를 관찰 |
| 고정·관찰 대상 | SQS | queue 구성을 고정하고 backlog/drain을 관찰 |
| 외부 통제 변수 | Demo AI | 고정된 synthetic Application Target으로 사용; 자체 capacity는 측정·tuning하지 않음 |

RDS instance class 단계 변경, RDS sizing 비교, storage/IOPS/connection pool 튜닝은 MVP 범위에
포함하지 않는다. RDS는 configured capacity snapshot과 observed metrics를 통해 실행 조건의
재현성과 병목 분석을 지원한다.

주요 Capacity 실험은 다음 축을 바꾸는 작업이다.

```text
동일 Dataset + 동일 Profile/Workload + ECS/SageMaker Capacity 변경
```

`PERF_ECS_CLUSTER`와 `PERF_ECS_SERVICE`는 실행 대상 Performance ECS 환경을 가리켜야 한다.
Runner는 실행 직전에 해당 Service가 사용하는 active Task Definition과 Service 상태를 AWS에서
읽어 `result.json.infrastructure_capacity.ecs`에 다음 값을 저장한다.

| IaC 입력 또는 상태 | snapshot artifact |
| --- | --- |
| Performance 전용 CPU 입력이 반영된 Task Definition | `ecs.task_cpu` |
| Performance 전용 memory 입력이 반영된 Task Definition | `ecs.task_memory` |
| Performance Service의 configured desired count | `ecs.desired_count` |
| snapshot 시점의 실제 running task 수 | `ecs.running_task_count` |

Snapshot은 Terraform 입력값 자체가 아니라 AWS에 적용된 실제 구성값을 기록한다. 그러므로
Terraform apply와 Performance Service rollout이 끝난 뒤 실행해야 하며, snapshot의 CPU/memory가
변경한 입력과 다르면 실행 조건을 재현할 수 없는 것으로 보고 실행을 중단하거나 결과를 비교하지
않는다. dev capacity는 dev 대상의 별도 실행에서만 기록하며 Performance Profile/Workload
schema에는 CPU, memory, capacity level 또는 ECS 식별자를 추가하지 않는다.

## Dataset과 Profile

Dataset은 무엇을 실행할지, Profile은 얼마나 실행할지를 소유한다.

| 구분 | 책임 | 예시 |
| --- | --- | --- |
| Dataset | TestSuite/TestCase 내용과 재현 가능한 TestCase 수 | `baseline-v1`, 78 TestCases |
| Profile | 동시 TestRun 수, ramp-up, duration, test type, acceptance criteria | `smoke`, `SMOKE` |

`performance/datasets/baseline-v1.yaml`은 기존 데모용 78건 import fixture를 immutable
source로 고정한다. 이 Dataset은 classifier 정확도 benchmark가 아니라 반복 가능한 performance
workload다. 기준으로 사용한 Dataset은 조용히 수정하지 않는다. TestCase를 추가하거나
생성 규칙을 바꾸려면 `baseline-v2` 또는 `perf-medium-v1`처럼 새 버전을 만들고 metadata에
생성 방법을 남긴다. `500 TestCases`는 Dataset 크기이며 `TARGET` Capacity와 같은 뜻이 아니다.

Profile type은 다음 의미를 갖는다.

- `SMOKE`: 설정과 파이프라인을 빠르게 확인하는 최소 실행
- `LOAD`: 정해진 평균 부하에서 지속 처리량 확인
- `PEAK`: 별도로 합의한 peak 동시성 확인
- `STRESS`: 용량 한계를 찾는 단계적 증가 실험
- `SOAK`: 장시간 실행에서 backlog, memory, 오류 누적 확인

`smoke.yaml`만 현재 실행 가능한 초기 Profile이다. `target`, `peak`, `stress`, `soak`의
최종 숫자는 Capacity Target 결정 후 같은 형식으로 추가한다.

## 책임 경계

```text
Python Runner: profile/dataset 검증 · preflight · seed · k6 orchestration
                                      · async drain · AWS metric · report/판정
k6: POST latency/error · TestRun 상태 polling · completion duration/failure metric
AWS collector: ECS/SQS/RDS/SageMaker CloudWatch 시계열
```

k6가 종료됐다는 사실만으로 테스트를 종료하지 않는다. Runner는 생성 구간의 TestRun이
`QUEUED/PREPARING/RUNNING`에 남아 있지 않고 source queue가 drain될 때까지 기다린다.
polling은 초기 Smoke Profile의 측정 경계이며, 대규모 Profile에서 traffic 왜곡이 확인되면
completion collector를 별도 구현으로 교체한다.

## 설치와 실행

필수 도구는 Python 3.11 이상, k6, 실행 대상 GuardBench API, 그리고 배포 환경에서는 AWS
SQS/CloudWatch를 조회할 자격 증명이다.

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r performance/requirements.txt

export APP_REVISION=<runner-source-commit-sha>
export INFRA_REVISION=<infrastructure-commit-sha>
export PERF_BASE_URL=https://<guardbench-api>
export PERF_TARGET_URL=https://<openai-compatible-target>/v1/chat/completions
export PERF_TARGET_MODEL=<model>
export PERF_TARGET_REVISION=<application-revision>
export PERF_SOURCE_QUEUE_URLS=<resolve-url>,<work-items-url>,<finalize-url>
export PERF_DLQ_URLS=<resolve-dlq-url>,<work-items-dlq-url>,<finalize-dlq-url>
export AWS_REGION=ap-northeast-2
export PERF_ECS_CLUSTER=<performance-cluster-name>
export PERF_ECS_SERVICE=<performance-service-name>
export PERF_RDS_INSTANCE_ID=<db-instance-id>
export PERF_SAGEMAKER_ENDPOINT_NAME=<classifier-endpoint-name>
export PERF_SAGEMAKER_VARIANT_NAME=<classifier-variant-name>
# CloudWatch SQS QueueName dimensions (source + DLQ)
export PERF_SOURCE_QUEUE_RESOLVE_NAME=<resolve-queue-name>
export PERF_SOURCE_QUEUE_WORK_ITEMS_NAME=<work-items-queue-name>
export PERF_SOURCE_QUEUE_FINALIZE_NAME=<finalize-queue-name>
export PERF_DLQ_RESOLVE_NAME=<resolve-dlq-name>
export PERF_DLQ_WORK_ITEMS_NAME=<work-items-dlq-name>
export PERF_DLQ_FINALIZE_NAME=<finalize-dlq-name>

# 입력과 실행 계획만 검증한다. API, AWS, DB를 호출하지 않는다.
python3 -m performance.runner.cli --dry-run

# 실제 비교 실행: preflight → Dataset seed → k6 → drain → metrics → report
python3 -m performance.runner.cli
```

Performance API가 internal ALB 뒤에 있으면 Worker 설정의
`guardbench.http-endpoint.allowed-private-hostnames`에 ALB의 전체 hostname을 정확히
등록한다. `allow-private-addresses=true`를 사용하지 않으며, wildcard나 private IP를
allowlist에 등록하지 않는다. 예를 들어 배포 환경 설정은 다음과 같다.

```yaml
guardbench:
  http-endpoint:
    allow-private-addresses: false
    allowed-private-hostnames:
      - internal-performance-api-123.ap-northeast-2.elb.amazonaws.com
```

실행 결과는 `performance/results/<run-id>/`에 `profile.yaml`, `dataset.yaml`,
`k6-summary.json`, `aws-metrics.json`, `result.json`, `report.md`로 저장한다. `result.json`의
`infrastructure_capacity`는 실행 전 AWS API에서 읽은 configured capacity snapshot이며,
`revisions`와 capture 시각을 함께 보존한다. `aws-metrics.json`은 실행 중 관측한 utilization,
latency, queue 등의 시계열을 별도로 보존한다. `report.md`의 Application/Infrastructure
revision과 snapshot의 revision은 `APP_REVISION`, `INFRA_REVISION` 환경변수로 주입한다.

## Spot Runner image와 결과 보존

Performance Runner는 private Spot host의 container runtime에서 직접 실행하는 Docker image다.
Image에는 Python dependency, k6와 canonical HTTP fixture를 함께 포함하므로 host의 실행 도구나 실행 중 Git clone/package
download에 의존하지 않는다.

Image repository와 URI는 배포 환경이 결정해 외부 입력으로 전달한다. Backend Git SHA는 image의
`APP_REVISION` 환경변수와 OCI revision label에 기록한다.

```bash
repository=<account>.dkr.ecr.<region>.amazonaws.com/<repository>
performance/build-runner-image.sh "$repository"

# Build script가 출력한 현재 Git SHA tag의 image reference를 ECR login 후 publish한다.
bin/publish-runner-image \
  "$repository:$(git rev-parse HEAD)"
```

Build script는 호출자가 전달한 repository URI에 현재 Backend Git SHA를 단 하나의 tag로 붙인다.
따라서 image tag, `APP_REVISION`, OCI revision label은 항상 같은 revision을 가리키며, 호출자가
서로 다른 tag/revision 조합을 지정할 수 없다.

Container 시작점은 `python3.11 -m performance.runner.cli`이며 기존 `--dry-run`,
`--profile`, `--dataset` 옵션을 그대로 사용한다. `/workspace/bin/verify-runtime`은 container
내부의 Python, k6, profile, dataset, canonical fixture와
`performance.runner` import를 확인하고 하나라도 없으면 non-zero로 종료한다.

실제 Runner는 `PERFORMANCE_RESULTS_BUCKET`이 설정된 경우, 결과 파일을 모두 만든 뒤 다음
prefix에 업로드한다. 따라서 PASS와 acceptance FAIL(k6 exit 99 포함)은 모두 보존된다. S3 upload
실패는 정상 성능 판정과 구분되는 Runner 오류이며, 로컬 결과는 남긴다. 환경변수가 없는 로컬
실행은 기존처럼 결과를 로컬에만 보존한다.

```text
s3://<PERFORMANCE_RESULTS_BUCKET>/performance/results/<run-id>/
```

로컬 API에 대한 입력 검증은 `--dry-run`으로 수행할 수 있다. 실제 비동기 Smoke 실행은
SQS와 Worker가 동작하는 로컬 또는 dev 배포가 필요하다. LocalStack을 사용할 때는
`PERF_SOURCE_QUEUE_URLS`와 `PERF_DLQ_URLS`에 LocalStack URL을 지정하고
`PERF_SQS_ENDPOINT_URL`을 추가한다. AWS metric이 없는 로컬 실행은 성능 판정용 Baseline으로
취급하지 않는다.

## 테스트 전 상태와 seed

Runner는 실행 전 다음을 확인하고 하나라도 실패하면 k6를 시작하지 않는다.

1. `GET /api/v1/test-suites?page=1&size=1`이 200인지 확인
2. 처리 중인 이전 TestRun이 없는지 확인
3. Source Queue의 visible/in-flight/delayed 메시지가 없는지 확인
4. DLQ에 메시지가 없는지 확인

Preflight가 통과하면 Runner는 seed/k6 전에 ECS service와 task definition, RDS instance,
SageMaker endpoint의 실제 configured capacity를 조회한다. 이 조회에 필요한 resource
identifier가 없거나 AWS 응답이 불완전하거나 조회가 실패하면 `unknown`으로 성공 처리하지
않고 Runner를 중단한다. 따라서 저장된 snapshot은 다음 값을 포함한다.

- ECS: cluster/service identifier, desired count, running task count, task CPU/memory
- RDS: DB instance identifier/class
- SageMaker: endpoint/production variant, instance type, desired/current instance count

`running task count`는 snapshot 시점의 ECS service 응답값이며, 실행 구간의 관측 시계열과
혼동하지 않는다. RDS instance identifier/class는 RDS를 조절하기 위한 입력이 아니라
Performance 전용 격리 DB의 실행 조건을 재현하기 위한 metadata로 계속 기록한다.

Runner는 DB reset이나 migration을 실행하지 않으며 DB 접속 정보도 요구하지 않는다.
DB lifecycle과 migration은 배포 환경과 Backend application이 소유한다. `--reset` 옵션과
기존 reset/migration 환경변수는 제거되었으므로 bootstrap 호출에서도 제거해야 한다
([IaC #50](https://github.com/GuardBench/guardbench-iac/issues/50)).

매 실행마다 `baseline-v1`으로 신규 TestSuite를 만들고 seed 결과의 실제 `testSuiteId`를 k6에
전달한다. 결과 진단은 현재 Suite와 실행 시간 범위로 제한한다. RDS는 capacity와 metric
관측 대상으로 유지한다. 실제 실행에는 `APP_REVISION`, `INFRA_REVISION`, `PERF_BASE_URL`과
SQS CloudWatch QueueName 설정이 필요하다.

## Metric과 판정

k6 threshold는 실행 전에 Profile에서 읽는다.

- TestRun 생성 API latency: p50, p95, p99
- TestRun 생성 HTTP error rate
- TestRun async completion duration과 terminal failure rate

Runner assertion은 k6 종료 후에도 평가한다.

- 생성된 Run이 모두 terminal `FINISHED`인지
- source queue가 drain됐는지와 drain 시간
- DLQ visible message 수

CloudWatch는 실행 시작~완료 구간으로 다음 시계열을 수집한다.

주요 Capacity 실험 대상의 관측 지표:

- ECS: `CPUUtilization`, `MemoryUtilization`, `RunningTaskCount`
- SageMaker classifier: `Invocations`, `ModelLatency`, `OverheadLatency`,
  `Invocation4XXErrors`, `Invocation5XXErrors`

고정·관찰 대상의 관측 지표:

- SQS: source/DLQ visible messages, source oldest message age
- RDS: `CPUUtilization`, `DatabaseConnections`

Configured capacity snapshot과 CloudWatch observed metrics는 서로 다른 artifact 영역이다.
MVP에서 `result.json.infrastructure_capacity`의 ECS/SageMaker 값은 주요 Capacity 비교의
실행 조건으로 보고, RDS instance identifier/class는 고정된 Performance 전용 DB를 재현하기
위한 metadata로 본다. `aws-metrics.json`의 ECS/SageMaker utilization·latency와 RDS/SQS
CPU·connection·backlog 지표는 해당 조건에서의 관찰 결과로 해석한다.

SageMaker metric은 `EndpointName + VariantName` dimension으로 조회한다. 성능 Baseline에서는
classifier가 실제 E2E 경로의 일부이므로 ECS·SQS·RDS와 함께 SageMaker datapoint도 있어야
AWS metric 수집 acceptance가 통과한다. 리소스 이름은 `performance/metrics/aws.yaml`과
환경변수에 두며, dimension이 비어 있는 metric은 `aws-metrics.json`에 `skipped`로 남긴다.

`TestRun create 202`는 E2E 성공과 다르다. k6는 `FINISHED`까지 polling하고
`executionOutcome=COMPLETED`인지 별도로 확인한다. Classifier provider 실패는 임의 ALLOW/BLOCK으로
fallback하지 않으며 execution failure로 반영된다. 병목 분석에서는 TestRun completion과
ECS/SQS/RDS를 SageMaker latency/error 시계열과 함께 확인해 GuardBench 내부 적체와 classifier
provider 지연을 구분한다.

HTTP threshold나 시스템 assertion을 위반하면 결과는 `FAIL`로 저장된다. Profile 목표값을
실행 후 바꾸어 결과를 PASS로 만들지 않는다. `report.md`의 metric 시계열과 workload를 함께
보고 병목 관찰을 기록하며, 결과가 0건이면 `totalPages`를 추정하지 않는다.

k6 threshold breach의 exit code `99`는 실행 오류가 아니라 측정 결과로 처리한다. Runner는
summary를 읽고 drain·CloudWatch 수집·acceptance 판정까지 마친 뒤 `result.json`과 `report.md`에
threshold `FAIL` 및 exit code를 남긴다. script 오류나 실행 불가처럼 다른 non-zero exit code는
Runner 오류로 중단한다.

## Baseline 비교와 비용

Baseline 비교 시 실행 전 preflight를 통과하고 아래 항목을 고정한다.

- 동일 Dataset version과 TestCase 수
- 동일 Profile과 acceptance criteria
- 동일한 Demo AI image/revision, model/config, ECS CPU/memory, desired count와 endpoint
- 가능한 한 동일 Application/Infrastructure revision 기록 방식
- 테스트 시작 전 active TestRun 없음과 queue/DLQ empty 상태

이전 Suite/TestRun/Execution/Outbox는 DB에 남는다. 실행별 Suite/time window로 결과를
분리하며, 누적 DB 크기와 index 상태는 완전히 동일한 조건으로 간주하지 않는다.

`concurrent_test_runs`는 단순 POST 동시 요청 수가 아니라 각 VU가 하나의 TestRun을 접수한 뒤
`FINISHED`까지 polling하는 동안 살아 있는 비동기 TestRun 수에 가깝다. 현재 `smoke`는
`max_iterations_per_vu: 1`이므로 1 VU가 1건을 완료하고 끝나는 Smoke이며, `duration_seconds`
동안 지속적으로 새 Run을 생성하는 Profile이 아니다. LOAD/STRESS/SOAK Profile에서는 이
필드를 0(제한 없음) 또는 적절한 반복 수로 명시해 생성률과 완료 polling traffic을 함께
검토한다.

ECS/SageMaker Capacity만 바꾸고 Dataset/Profile, Demo AI 조건, RDS/SQS 구성을 유지해야 CPU, memory,
queue age, completion time의 변화를 원인 후보로 비교할 수 있다. RDS/SQS 지표는 고정된
의존 인프라에서 발생한 병목 여부를 확인하는 데 사용한다. `target`, `peak`, `stress` 숫자는
Capacity Target 논의가 끝난 뒤 추가한다. Profile의 동시 TestRun 수 × Dataset TestCase 수 ×
retry 가능성을 실행 전에 확인해 SageMaker Runtime 요청량과 AWS 비용을 검토한다.

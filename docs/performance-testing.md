# 성능 테스트 운영

> Status: APPROVED
> Owner: Backend
> Related Issue: #140

## 목적과 원칙

GuardBench 성능 테스트는 현재 ECS 설정값을 목표로 삼지 않고, Capacity Target을 먼저 정한
뒤 동일한 workload를 반복 실행해 Baseline과 인프라 변경 전후를 비교하기 위한 도구다.
TestRun은 `POST /api/v1/test-runs`의 `202 Accepted`에서 끝나지 않고 SQS/Worker를 거쳐
`FINISHED`가 되므로 HTTP latency와 비동기 완료 시간을 분리해서 측정한다.

실험 순서는 다음과 같다.

```text
Capacity Target → Performance Profile → 동일 Dataset 실행 → Baseline
→ 병목 분석 → 인프라 변경 → 동일 workload 재검증
```

현재 단일 ECS Service는 최초 Baseline 측정 대상일 뿐 성능 목표의 근거가 아니다. API/Worker
분리, Worker Auto Scaling, Fargate 자원 조정 이후에도 같은 Dataset과 Profile을 사용한다.

## Dataset과 Profile

Dataset은 무엇을 실행할지, Profile은 얼마나 실행할지를 소유한다.

| 구분 | 책임 | 예시 |
| --- | --- | --- |
| Dataset | TestSuite/TestCase 내용과 재현 가능한 TestCase 수 | `baseline-v1`, 78 TestCases |
| Profile | 동시 TestRun 수, ramp-up, duration, test type, acceptance criteria | `small`, `SMOKE` |

`performance/datasets/baseline-v1.yaml`은 기존 데모용 78건 import fixture를 immutable
source로 고정한다. 기준으로 사용한 Dataset은 조용히 수정하지 않는다. TestCase를 추가하거나
생성 규칙을 바꾸려면 `baseline-v2` 또는 `perf-medium-v1`처럼 새 버전을 만들고 metadata에
생성 방법을 남긴다. `500 TestCases`는 Dataset 크기이며 `TARGET` Capacity와 같은 뜻이 아니다.

Profile type은 다음 의미를 갖는다.

- `SMOKE`: 설정과 파이프라인을 빠르게 확인하는 최소 실행
- `LOAD`: 정해진 평균 부하에서 지속 처리량 확인
- `PEAK`: 별도로 합의한 peak 동시성 확인
- `STRESS`: 용량 한계를 찾는 단계적 증가 실험
- `SOAK`: 장시간 실행에서 backlog, memory, 오류 누적 확인

`small.yaml`만 현재 실행 가능한 초기 Profile이다. `target`, `peak`, `stress`, `soak`의
최종 숫자는 Capacity Target 결정 후 같은 형식으로 추가한다.

## 책임 경계

```text
Python Runner: profile/dataset 검증 · preflight · reset/seed · k6 orchestration
                                      · async drain · AWS metric · report/판정
k6: POST latency/error · TestRun 상태 polling · completion duration/failure metric
AWS collector: ECS/SQS/RDS CloudWatch 시계열
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

export PERF_BASE_URL=https://<guardbench-api>
export PERF_TARGET_URL=https://<openai-compatible-target>/v1/chat/completions
export PERF_TARGET_MODEL=<model>
export PERF_TARGET_REVISION=<application-revision>
export PERF_SOURCE_QUEUE_URLS=<resolve-url>,<work-items-url>,<finalize-url>
export PERF_DLQ_URLS=<resolve-dlq-url>,<work-items-dlq-url>,<finalize-dlq-url>
export AWS_REGION=ap-northeast-2
export PERF_ECS_CLUSTER=<cluster-name>
export PERF_ECS_SERVICE=<service-name>
export PERF_RDS_INSTANCE_ID=<db-instance-id>
# CloudWatch SQS QueueName dimensions (source + DLQ)
export PERF_SOURCE_QUEUE_RESOLVE_NAME=<resolve-queue-name>
export PERF_SOURCE_QUEUE_WORK_ITEMS_NAME=<work-items-queue-name>
export PERF_SOURCE_QUEUE_FINALIZE_NAME=<finalize-queue-name>
export PERF_DLQ_RESOLVE_NAME=<resolve-dlq-name>
export PERF_DLQ_WORK_ITEMS_NAME=<work-items-dlq-name>
export PERF_DLQ_FINALIZE_NAME=<finalize-dlq-name>

# 입력과 실행 계획만 검증한다. API, AWS, DB를 호출하지 않는다.
python3 -m performance.runner.cli --dry-run

# 실제 비교 실행: preflight → reset/migration → Dataset seed → k6 → drain → metrics → report
python3 -m performance.runner.cli --reset
```

실행 결과는 `performance/results/<run-id>/`에 `profile.yaml`, `dataset.yaml`,
`k6-summary.json`, `aws-metrics.json`, `result.json`, `report.md`로 저장한다. `report.md`의
Application/Infrastructure revision은 `APP_REVISION`, `INFRA_REVISION` 환경변수로 주입한다.

## Spot Runner artifact와 결과 보존

IaC의 AL2023 Spot Runner는 private subnet에서 다음 bootstrap artifact를 받는다. artifact는
Python dependency, k6, PostgreSQL client, Java 21, Gradle dependency cache, backend migration과
canonical HTTP fixture를 함께 제공하므로 실행 중 Git clone이나 package download가 필요 없다.

```bash
# Docker가 있는 Backend repository에서 실행한다.
APP_REVISION="$(git rev-parse HEAD)" performance/artifact/build-runner-artifact.sh

# IaC bootstrap contract에 publish한다.
export PERFORMANCE_RESULTS_BUCKET=<terraform output performance_results_bucket_name>
export APP_REVISION="$(git rev-parse HEAD)"
bin/publish-runner-artifact
```

압축 해제 후 SSM bootstrap은 `/opt/guardbench-performance-runner/bin/verify-runtime`을 실행한다.
이 검증은 runtime, Flyway/Gradle 입력, profile, dataset, canonical fixture와
`performance.runner` import를 확인하고 하나라도 없으면 non-zero로 종료한다. artifact의
`ARTIFACT-METADATA.json`에는 Backend revision과 k6 version이 포함된다.

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

## 테스트 전 상태와 reset/seed

Runner는 실행 전 다음을 확인하고 하나라도 실패하면 k6를 시작하지 않는다.

1. `GET /api/v1/test-suites?page=1&size=1`이 200인지 확인
2. 처리 중인 이전 TestRun이 없는지 확인
3. Source Queue의 visible/in-flight/delayed 메시지가 없는지 확인
4. DLQ에 메시지가 없는지 확인

모든 비교 가능한 실제 실행은 `--reset`을 필수로 요구한다. `--reset`은 Dataset seed 전에 DB를
초기화하고 애플리케이션이 소유한 Flyway를 non-web Boot run으로 실행한다. 별도 performance
DB에서만 허용되며 아래 세 가지 guard와 연결 URL의 database name이 모두 정확히 일치하지
않으면 reset 명령 자체가 실행되지 않는다.

```bash
export PERFORMANCE_ENVIRONMENT=performance
export PERFORMANCE_DB_NAME=guardbench_perf
export PERFORMANCE_RESET_CONFIRM=RESET_GUARDBENCH_PERF
export PERFORMANCE_DATABASE_URL=postgresql://<performance-rds>:5432/guardbench_perf
# Optional: JDBC 옵션이 필요할 때만 지정한다. host/port/database는 위 URL과 동일해야 한다.
export PERFORMANCE_DATABASE_JDBC_URL=jdbc:postgresql://<performance-rds>:5432/guardbench_perf?sslmode=require
export PERFORMANCE_DB_USERNAME=guardbench
export PERFORMANCE_DB_PASSWORD=<secret>
python3 -m performance.runner.cli --reset
```

Runner는 `PERFORMANCE_DATABASE_URL`의 scheme/host/database name을 먼저 확인하고, destructive
command와 같은 PostgreSQL connection에서 `SELECT current_database()` 결과가
`guardbench_perf`인지 확인한 뒤에만 schema를 삭제한다. `PERFORMANCE_DB_NAME`은 이 실제 URL
database name과 함께 검증된다. RDS instance identifier를 별도 문자열로 신뢰하지 않는다.
AWS RDS를 사용할 때는 운영자가 URL host와 RDS endpoint를 별도로 대조해야 한다.

Flyway는 기본적으로 `PERFORMANCE_DATABASE_URL`에서 JDBC URL을 파생해 같은 DB에만 실행한다.
`PERFORMANCE_DATABASE_JDBC_URL`을 명시할 때도 `jdbc:postgresql` 형식이며 URL의 host, port,
database가 reset URL과 정확히 같아야 한다. 하나라도 다르면 migration 시작 전에 중단한다.

`PERFORMANCE_MIGRATION_COMMAND_JSON`을 설정하면 기본 non-web Boot migration 명령을 명시적인
문자열 배열로 대체할 수 있다. reset은 운영/개발 공용 DB, database name이 다른 DB, 확인 token이
없는 환경에서 금지한다. reset 후에는 동일 migration과 `baseline-v1` seed 결과의 실제
`testSuiteId`를 k6에 전달하므로 Profile이나 TestCase가 PK를 고정하지 않는다.

## Metric과 판정

k6 threshold는 실행 전에 Profile에서 읽는다.

- TestRun 생성 API latency: p50, p95, p99
- TestRun 생성 HTTP error rate
- TestRun async completion duration과 terminal failure rate

Runner assertion은 k6 종료 후에도 평가한다.

- 생성된 Run이 모두 terminal `FINISHED`인지
- source queue가 drain됐는지와 drain 시간
- DLQ visible message 수

CloudWatch는 실행 시작~완료 구간으로 ECS `CPUUtilization`, `MemoryUtilization`,
`RunningTaskCount`, SQS visible/oldest age, RDS `CPUUtilization`과
`DatabaseConnections`를 수집한다. 리소스 이름은 `performance/metrics/aws.yaml`과 환경변수에
두며 Runner는 ECS 단일 Service인지 API/Worker 분리인지 가정하지 않는다. 리소스 dimension이
없는 metric은 `aws-metrics.json`에 `skipped`로 남긴다. AWS acceptance는 요청 성공만으로
통과하지 않으며 ECS·SQS·RDS 각 그룹에 실제 datapoint가 하나 이상 있어야 통과한다.

HTTP threshold나 시스템 assertion을 위반하면 결과는 `FAIL`로 저장된다. Profile 목표값을
실행 후 바꾸어 결과를 PASS로 만들지 않는다. `report.md`의 metric 시계열과 workload를 함께
보고 병목 관찰을 기록하며, 결과가 0건이면 `totalPages`를 추정하지 않는다.

k6 threshold breach의 exit code `99`는 실행 오류가 아니라 측정 결과로 처리한다. Runner는
summary를 읽고 drain·CloudWatch 수집·acceptance 판정까지 마친 뒤 `result.json`과 `report.md`에
threshold `FAIL` 및 exit code를 남긴다. script 오류나 실행 불가처럼 다른 non-zero exit code는
Runner 오류로 중단한다.

## Baseline 비교와 비용

Baseline 비교 시 매 실행마다 `--reset`으로 다음 상태를 재현하고 아래 항목을 고정한다.

- 동일 Dataset version과 TestCase 수
- 동일 Profile과 acceptance criteria
- 가능한 한 동일 Application/Infrastructure revision 기록 방식
- 테스트 시작 전 queue/DLQ와 DB 상태

reset 없는 seed 반복 실행은 허용하지 않는다. 따라서 이전 Suite/TestRun/Execution/Outbox가
다음 실행에 누적되어 DB 크기와 index 상태를 바꾸는 경로가 없다.

`concurrent_test_runs`는 단순 POST 동시 요청 수가 아니라 각 VU가 하나의 TestRun을 접수한 뒤
`FINISHED`까지 polling하는 동안 살아 있는 비동기 TestRun 수에 가깝다. 현재 `small`은
`max_iterations_per_vu: 1`이므로 1 VU가 1건을 완료하고 끝나는 Smoke이며, `duration_seconds`
동안 지속적으로 새 Run을 생성하는 Profile이 아니다. LOAD/STRESS/SOAK Profile에서는 이
필드를 0(제한 없음) 또는 적절한 반복 수로 명시해 생성률과 완료 polling traffic을 함께
검토한다.

인프라 구조만 바꾸고 Dataset/Profile은 유지해야 CPU, memory, queue age, completion time의
변화를 원인 후보로 비교할 수 있다. `target`, `peak`, `stress` 숫자는 Capacity Target 논의가
끝난 뒤 추가한다. Profile의 동시 TestRun 수 × Dataset TestCase 수 × retry 가능성을 실행 전에
확인해 Bedrock 요청량과 AWS 비용을 검토한다.

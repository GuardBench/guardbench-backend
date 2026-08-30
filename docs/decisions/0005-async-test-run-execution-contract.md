# 0005. 비동기 TestRun 실행 계약

> ⚠️ v1 role 메시지, `(snapshotId, targetType)` 작업, Baseline/Candidate fan-out 부분은 [ADR 0010](0010-single-target-test-run-model.md)이 대체한다.

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-27
> Canonical source: GitHub
> Origin: [GitHub Issue #5](https://github.com/GuardBench/guardbench-backend/issues/5)

- ADR Status: ACCEPTED
- Decision date: 2026-08-25
- Related Issue: #5
- Superseded in part by: [ADR 0006](0006-independent-domain-contract-boundaries.md) — `evaluation -> testrun` Java Domain 의존 해석
- Extended by: [ADR 0008](0008-async-testrun-persistence-contract.md) — Outbox, claim과 HTTP Idempotency 물리 계약
- Implementation map: [비동기 TestRun 계약 맵](../contracts/README.md) (`DRAFT`; 계약 키별 Primary contract와 보조 참조를 찾게 할 뿐 ADR의 승인 내용을 대체하지 않음)
- Implementation docs:
  - [TestRun Persistence 구현 인덱스](../architecture/testrun-persistence.md) — Outbox·claim·idempotency 물리 산출물 위치
  - [애플리케이션 오류 코드: TestExecution 실행 오류 Code](../conventions/application-errors.md#testexecution-실행-오류-code) — 공개 가능한 오류 code 목록

## Context

TestRun 접수 뒤 Candidate DRAFT를 고정하고, Snapshot별 Baseline/Candidate 실행을 여러 Worker에 분배한 다음 Snapshot 평가와 Quality Gate로 수렴시키는 비동기 실행 계약이 필요하다.

기존 승인 계약은 다음을 이미 규정한다.

- TestRun 접수 트랜잭션은 `QUEUED` TestRun, TestCaseSnapshot과 `TestRunRequested` Outbox를 함께 저장한다.
- [ADR 0003](0003-result-aggregate-and-write-port-boundaries.md)의 `TestExecution`은 `(TestCaseSnapshotId, TargetType)`으로 식별되는 터미널 결과 Aggregate다.
- `SnapshotEvaluation`은 Candidate ActualResult가 있을 때 Assertion을 필수로, Baseline ActualResult도 있을 때 Change를 선택적으로 소유한다.
- [ADR 0004](0004-testrun-finalization-atomicity.md)에 따라 QualityGateResult 저장과 TestRun `FINISHED` 전환은 원자적이며, 완료 후 재호출은 기존 결과를 반환한다.

이 ADR은 위 계약을 바꾸지 않고 Outbox, SQS 메시지, Orchestrator/Executor 작업 분할, Worker 선점, 재시도, timeout, DLQ와 동시 최종화 방식을 결정한다.

결정에는 다음 제약을 적용한다.

- SQS Standard의 중복 전달과 순서 뒤바뀜을 정상 상황으로 취급한다.
- 동일한 TestExecution을 여러 Executor가 동시에 받아도 터미널 결과는 하나로 수렴해야 한다. lease 만료 경계의 중복 Provider 호출은 허용하되 claim으로 빈도와 저장 영향을 제한한다.
- Bedrock 호출 중 DB 트랜잭션, row lock 또는 connection을 유지하지 않는다.
- 메시지는 실행 입력이나 결과를 복제하지 않고 불변 식별자만 전달한다.
- PostgreSQL의 TestRun, TestExecution과 Evaluation 결과를 현재 상태의 Source of Truth로 사용한다.
- `TestExecution`에는 `QUEUED`나 `RUNNING`을 추가하지 않고 승인된 네 터미널 상태만 저장한다.
- HTTP 요청 멱등성, Outbox 재발행, Worker 선점, 결과 PK와 최종화 멱등성을 서로 다른 경계로 구분한다.
- Provider 실행 실패와 DB·SQS 같은 기술 실패를 같은 TestExecution 오류로 취급하지 않는다.
- 정상적인 Provider 실패는 제한된 시간 안에 `FAILED` 또는 `TIMED_OUT`으로 수렴시킨다.
- 구현 우선의 MVP 범위를 유지하며 자동 stale-run 복구와 PUBLISHED Outbox 정리는 후속 작업으로 둔다.

## Decision

### 런타임 역할과 Queue

```text
API ECS Fargate
  -> TestRun + Snapshot + TestRunRequested Outbox

gb-run-resolve (SQS Standard)
  -> Orchestrator ECS Fargate Service
  -> Candidate materialization + TestExecution fan-out

gb-workitems (SQS Standard)
  -> Executor ECS Fargate Service
  -> Bedrock ApplyGuardrail + terminal TestExecution

gb-run-finalize (SQS Standard)
  -> Orchestrator ECS Fargate Service
  -> SnapshotEvaluation + Quality Gate + TestRun finalization
```

- Orchestrator는 하나의 논리적 ECS Service다. backlog에 따라 같은 Service의 Task 인스턴스가 여러 개 실행될 수 있다.
- EventBridge가 메시지마다 Orchestrator Task를 생성하는 방식은 사용하지 않는다.
- Executor는 `gb-workitems`의 TestExecution을 소비한다.
- Outbox Publisher는 API Fargate의 독립 background component로 실행한다.
- MVP에서는 Publisher가 계속 진행되도록 API Service의 최소 Task 수를 1로 유지한다. API와 Publisher의 완전한 scale-to-zero 또는 별도 배포는 후속 범위다.
- 세 Queue는 모두 SQS Standard를 사용하며 중복과 역순 처리를 소비자 멱등성으로 흡수한다.

### v1 메시지 계약

모든 메시지는 다음 공통 필드를 갖는다.

| 필드 | 계약 |
| --- | --- |
| `eventId` | UUID 문자열. 같은 Outbox의 재발행에도 유지한다. |
| `eventType` | 아래 세 타입 중 하나다. |
| `schemaVersion` | 숫자 `1`이다. |
| `testRunId` | 기존 숫자형 Domain ID를 그대로 직렬화한다. |
| `occurredAt` | UTC ISO-8601 문자열이다. |

`TestRunRequested`는 공통 필드만 사용한다.

```json
{
  "eventId": "0198e2ca-0000-7000-8000-000000000001",
  "eventType": "TestRunRequested",
  "schemaVersion": 1,
  "testRunId": 1234,
  "occurredAt": "2026-08-25T10:00:00Z"
}
```

`TestExecutionRequested`와 `TestExecutionCompleted`는 숫자형 `snapshotId`와 `BASELINE` 또는 `CANDIDATE`인 `targetType`을 추가한다.

```json
{
  "eventId": "0198e2ca-0000-7000-8000-000000000002",
  "eventType": "TestExecutionRequested",
  "schemaVersion": 1,
  "testRunId": 1234,
  "snapshotId": 5678,
  "targetType": "BASELINE",
  "occurredAt": "2026-08-25T10:00:01Z"
}
```

```json
{
  "eventId": "0198e2ca-0000-7000-8000-000000000003",
  "eventType": "TestExecutionCompleted",
  "schemaVersion": 1,
  "testRunId": 1234,
  "snapshotId": 5678,
  "targetType": "BASELINE",
  "occurredAt": "2026-08-25T10:00:10Z"
}
```

메시지에는 입력, ExpectedResult, ActualResult, Guardrail 설정 전문, Provider 오류 원문을 넣지 않는다. 완료 메시지에도 `executionStatus`를 복제하지 않으며 Orchestrator가 DB의 terminal TestExecution을 조회한다.

알 수 없는 optional 필드는 무시한다. 필수 필드 누락, 알 수 없는 `eventType`, 잘못된 필드 타입과 지원하지 않는 `schemaVersion`은 처리하지 않고 DLQ로 격리한다. optional 필드 추가는 v1에서 허용하고 기존 필드의 삭제·이름·타입·의미 변경은 새 schema version으로 올린다.

### 작업 단위와 fan-out

Queue의 작업 단위는 TestExecution 하나, 즉 `(snapshotId, targetType)` 하나다. 같은 Snapshot의 Baseline과 Candidate는 별도 메시지이며 서로 다른 Executor Task에서 실행될 수 있다. Snapshot이 50개면 `TestExecutionRequested`는 100개다.

Orchestrator는 `testRunId` 기준의 기술적 resolution claim/lease를 획득하고 TestRun을 `QUEUED -> PREPARING`으로 전환한다. Candidate DRAFT materialization은 DB 트랜잭션 밖에서 수행한다.

- Bedrock `CreateGuardrailVersion`에는 `guardbench-test-run-{testRunId}` 형태의 결정적 `clientRequestToken`을 전달한다.
- materialization 성공 후 현재 claim 소유를 재검증한다.
- 다음 항목을 하나의 DB 트랜잭션으로 저장한다.
  - `candidateResolvedVersion`
  - TestRun의 `PREPARING -> RUNNING` 전환
  - 모든 Snapshot의 Baseline/Candidate `TestExecutionRequested` Outbox
- 실행 요청의 논리적 중복 키는 `eventType + snapshotId + targetType`이며 unique constraint로 중복 fan-out을 막는다.
- 외부 호출 성공 뒤 DB commit이 실패해도 같은 `clientRequestToken`으로 재시도한다.

materialization이 영구 실패하거나 재시도를 소진하면 실행 메시지를 만들지 않는다. 대신 하나의 Application 트랜잭션에서 다음 결과로 종결한다.

- 모든 Snapshot의 Baseline/Candidate TestExecution을 `NOT_STARTED`로 저장한다.
- `processedTestCaseCount = testCaseCount`로 저장한다.
- QualityGateResult는 `NOT_EVALUATED`, `metrics = null`이다.
- TestRun은 `FINISHED`, `executionOutcome = ERROR`다.

### TestExecution claim과 결과 저장

`TestExecution`은 승인 계약대로 터미널 결과만 표현한다. 실행 중 선점은 별도 Infrastructure 테이블에서 관리한다.

```text
test_execution_claim
├─ snapshot_id
├─ target_type
├─ claim_token UUID
├─ lease_until
├─ attempt_count
├─ claimed_at
└─ updated_at

PK: (snapshot_id, target_type)
```

- Application 계약은 `testrun/application`의 `TestExecutionClaimPort`에 둔다.
- JPA Entity와 조건부 INSERT/UPDATE Adapter는 `testrun/infrastructure/persistence`에 둔다.
- claim 테이블과 Port는 Aggregate Repository가 아니라 정상적인 lease 기간의 중복 외부 호출을 줄이고 stale 결과 저장을 차단하는 기술 계약이다.
- 동일 실행 ID에 claim이 없거나 lease가 만료됐을 때만 새 token으로 선점한다.
- Bedrock 호출 전 짧은 트랜잭션에서 선점하고 DB connection을 반환한다.
- 결과 저장 시 현재 token과 같은지 다시 확인해 만료된 Worker의 늦은 결과를 거부한다.
- terminal TestExecution과 `TestExecutionCompleted` Outbox를 하나의 트랜잭션으로 최초 저장한다.
- 같은 ID의 terminal 결과를 다른 의미의 결과로 덮어쓰지 않는다.
- 이미 terminal 결과가 있으면 기존 결과를 인정하고 원본 메시지를 삭제한다.

[`ApplyGuardrail`](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ApplyGuardrail.html)은 idempotency 또는 fencing token을 받지 않는다. Worker A가 호출 도중 멈추고 lease가 만료된 뒤 Worker B가 새 claim으로 호출하면 A와 B의 Provider 호출이 겹칠 수 있다. 따라서 Provider 호출의 exactly-once는 보장하지 않으며, MVP의 보장 경계는 다음과 같다.

- Provider 호출은 lease 만료 경계에서 at-least-once가 될 수 있다.
- Provider timeout보다 긴 claim lease로 정상 흐름의 중복 호출 가능성을 낮춘다.
- 현재 claim token을 가진 Worker의 terminal TestExecution만 최초 저장한다.
- 만료된 Worker가 늦게 반환한 결과는 저장과 완료 이벤트 생성에 영향을 주지 않는다.

### 재시도, timeout, visibility와 DLQ

초기 운영값은 설정으로 분리하고 다음 값으로 시작한다.

| 항목 | 초기값 |
| --- | ---: |
| Provider 호출 전체 timeout | 15초 |
| 최대 Application 실행 시도 | 3회 |
| 한 메시지 수신당 Provider 호출 | 1회 |
| 재전달 간격 | 약 5초 |
| SQS visibility timeout | 30초 |
| execution claim lease | 45초 |
| DLQ `maxReceiveCount` | 5회 |
| visibility heartbeat | 사용하지 않음 |

`attemptCount`는 Provider 호출을 위해 획득한 execution claim 횟수다.

- 일시적인 Provider 오류가 3회 안에 성공하면 `SUCCEEDED`다.
- timeout이 소진되면 `TIMED_OUT`이다.
- 그 밖의 retryable Provider 오류가 소진되면 `FAILED`다.
- 영구 Provider 오류는 첫 시도에서 `FAILED`로 수렴할 수 있다.
- DB commit, SQS와 Outbox 오류는 TestExecution 실패로 바꾸지 않는다.
- 다른 Worker가 유효한 claim을 보유하면 실행하거나 메시지를 삭제하지 않고 lease 이후 다시 보이게 한다.
- 결과와 다음 Outbox가 commit된 후에만 원본 메시지를 삭제한다.
- 예상 가능한 Provider 실패는 DLQ 전에 terminal TestExecution으로 수렴한다.
- DLQ는 역직렬화·미지원 schema, 반복되는 Application 버그와 DB 장애처럼 정상 terminal 저장 자체가 불가능한 경우를 격리한다.
- DLQ 이동만으로 TestRun을 실패 처리하지 않는다. MVP에서는 경보 후 수동 redrive한다.

공개 가능한 TestExecution 오류 code는 다음으로 제한한다.

| Code | Terminal 상태 |
| --- | --- |
| `TARGET_NOT_FOUND` | `FAILED` |
| `TARGET_ACCESS_DENIED` | `FAILED` |
| `TARGET_CONFIGURATION_INVALID` | `FAILED` |
| `PROVIDER_UNAVAILABLE` | `FAILED` |
| `PROVIDER_RESPONSE_INVALID` | `FAILED` |
| `PROVIDER_TIMEOUT` | `TIMED_OUT` |

각 code에는 고정된 안전한 메시지를 사용한다. Provider 원문, SDK 예외 메시지, stack trace, ARN, 자격 증명과 내부 endpoint는 공개하지 않는다. 실제 Bedrock 응답·예외 매핑과 ActualResult 정규화는 Issue #17의 책임이다.

### 완료 평가와 최종화

모든 terminal TestExecution은 `TestExecutionCompleted`를 발행한다. 마지막 Worker를 별도로 판별하지 않는다.

Orchestrator는 각 완료 메시지를 받을 때 Bedrock 호출이 끝난 뒤 TestRun 행을 `FOR UPDATE`로 짧게 잠그고 다음을 수행한다.

1. 같은 Snapshot의 Baseline/Candidate terminal TestExecution을 조회한다.
2. 둘 중 하나가 아직 없으면 평가하지 않는다.
3. 둘 다 terminal이고 SnapshotEvaluation이 없을 때만 생성한다.
   - Candidate ActualResult가 있으면 Assertion을 생성한다.
   - Baseline ActualResult도 있으면 Change를 함께 생성한다.
   - Candidate ActualResult가 없으면 SnapshotEvaluation을 생성하지 않는다.
4. `processedTestCaseCount`를 증분하지 않고 두 target이 모두 terminal인 Snapshot의 절대 개수로 다시 계산한다.
5. 모든 Snapshot이 처리 완료면 QualityGateResult 저장과 TestRun `FINISHED` 전환을 ADR 0004에 따라 같은 트랜잭션에서 수행한다.
6. 이미 `FINISHED`이고 QualityGateResult가 있으면 기존 결과를 반환하는 멱등 성공으로 처리한다. 재계산하거나 덮어쓰지 않는다.

같은 TestRun의 완료 처리는 row lock으로 직렬화하지만 서로 다른 TestRun은 병렬 처리한다. `FINISHED`인데 QualityGateResult가 없거나 그 역인 상태는 ADR 0004와 같이 저장 불변식 위반이다.

### Outbox와 Publisher

Outbox의 최소 물리 계약은 다음과 같다.

```text
outbox_event
├─ event_id UUID PK
├─ event_type
├─ schema_version
├─ payload JSONB
├─ deduplication_key UNIQUE
├─ status              PENDING | PUBLISHED
├─ created_at
└─ published_at

INDEX: (status, created_at)
```

- 목적 Queue는 `eventType`으로 결정하고 별도 destination 값을 중복 저장하지 않는다.
- Publisher는 `SELECT ... FOR UPDATE SKIP LOCKED`로 PENDING batch를 겹치지 않게 가져간다.
- 잠근 batch를 SQS `SendMessageBatch`로 발행하고 성공 항목만 `PUBLISHED`로 변경한 뒤 commit한다.
- 실패 항목은 `PENDING`으로 남겨 다음 polling에서 같은 `eventId`로 재발행한다.
- 한 레코드의 발행 실패가 같은 batch의 나머지 레코드 처리를 중단하지 않는다.
- SQS 발행 성공 후 `PUBLISHED` commit 실패로 생긴 중복은 소비자의 Domain ID, 상태와 claim 멱등성으로 흡수한다.
- Outbox의 `DEAD`, 항목별 attempt와 next-attempt 추적, 자동 stale-run Reconciler와 PUBLISHED 자동 정리는 MVP에서 구현하지 않는다.

영구적인 PENDING Outbox 때문에 TestRun이 terminal에 도달하지 못할 수 있다. 이를 잘못된 TestExecution 실패로 변환하지 않고 오래된 PENDING을 경보한 뒤 장애를 복구하고 재발행한다.

### 원본 메시지 ack 규칙

- DB 변경과 다음 Outbox가 모두 commit된 뒤에만 원본 SQS 메시지를 삭제한다.
- 처리 대상이 이미 terminal이면 기존 결과를 인정하고 삭제한다.
- retryable 오류이거나 다른 Worker의 유효한 claim이 있으면 삭제하지 않는다.
- SQS 삭제 실패로 메시지가 다시 전달되어도 위 claim, 결과 PK와 최종화 규칙으로 같은 결과에 수렴한다.

### 의도적으로 남긴 운영 범위

다음 값과 기능은 이 ADR의 의미 계약을 바꾸지 않는 후속 운영·최적화 범위다.

- ECS Task 최소·최대 수와 backlog scaling threshold
- DB connection pool, SQS batch 크기와 polling 간격의 세부 운영값
- API와 Publisher의 완전 scale-to-zero 또는 Publisher 별도 배포
- PUBLISHED Outbox 보존 기간과 cleanup
- stale TestRun과 영구 PENDING 자동 Reconciler
- DLQ 자동 redrive와 운영 절차 자동화
- Provider가 지원하는 fencing/idempotency 또는 더 강한 `ApplyGuardrail` 중복 호출 억제

## Alternatives

### TestRun 전체를 하나의 작업 메시지로 실행

메시지와 조정 단계는 줄지만 Snapshot별 독립 확장과 재시도가 어렵다. `(snapshotId, targetType)` 단위 fan-out을 선택한다.

### SQS FIFO로 순서와 중복을 제어

이 흐름은 업무상 처리 순서를 요구하지 않으며 FIFO도 Application의 결과 멱등성을 대신하지 못한다. 순서 제약 없이 확장할 수 있는 Standard Queue를 선택한다.

### Baseline과 Candidate를 같은 Executor에 고정

DB 조회를 일부 줄일 수 있지만 한 target의 지연·실패와 다른 target의 재시도·확장을 결합한다. 두 TestExecution을 독립 메시지로 유지한다.

### SQS visibility나 결과 PK만으로 외부 호출 중복 방지

visibility 만료와 Worker 장애 뒤 재전달에서는 활성 Worker의 소유권을 판별할 수 없고, 결과 PK는 호출 뒤의 중복 저장만 막는다. claim/lease로 정상 흐름의 중복 호출을 줄이고 stale 결과를 차단하되 lease 만료 경계의 중복 호출은 허용한다.

### 실행 중 상태를 TestExecution Aggregate에 저장

`QUEUED`와 `RUNNING`을 추가하면 승인된 terminal-result Aggregate의 의미가 바뀐다. 선점 상태를 Infrastructure claim 테이블로 분리한다.

### 완료 counter를 이벤트마다 증가

중복 완료 메시지와 동시 처리에서 과다 계상될 수 있다. 두 target이 terminal인 Snapshot의 수를 다시 계산한다.

### 마지막 Worker만 완료 메시지 발행

마지막 여부를 결정하는 별도 경쟁과 counter가 필요하다. 모든 terminal 실행이 완료 이벤트를 발행하고 Orchestrator가 멱등하게 수렴시킨다.

### Publisher 전용 Service 또는 EventBridge RunTask

배포 단위와 scale-to-zero를 개선할 수 있지만 MVP 구현과 운영 구성이 늘어난다. API Fargate background component를 사용하고 분리는 후속 최적화로 둔다.

### Outbox 처리 상태와 자동 복구 확장

`PROCESSING`, `DEAD`, 항목별 retry schedule과 stale-run Reconciler는 관측과 자동 복구를 강화하지만 현재 범위를 크게 늘린다. `PENDING/PUBLISHED`와 경보·수동 복구로 시작한다.

## Consequences

장점은 다음과 같다.

- Snapshot과 target 단위로 Executor를 독립 확장하고 재시도할 수 있다.
- Standard Queue의 중복·역순·ack 실패가 terminal 저장 결과를 바꾸지 않는다.
- 장기 Provider 호출 동안 DB lock과 connection을 점유하지 않는다.
- 메시지는 식별자만 전달하고 DB를 Source of Truth로 유지한다.
- 기존 Aggregate, Repository 소유권과 `evaluation -> testrun` 의존 방향을 유지한다.
- materialization, 실행 저장과 최종화의 commit 경계가 구현·통합 테스트 단위로 드러난다.

비용과 위험은 다음과 같다.

- resolution claim, execution claim과 Outbox를 위한 기술 스키마와 Adapter가 필요하다.
- 한 Snapshot 실행을 위해 Baseline과 Candidate가 DB에서 각각 입력을 조회할 수 있다.
- API Task 하나를 항상 유지해야 Outbox Publisher가 진행된다.
- `FOR UPDATE SKIP LOCKED` Publisher는 SQS batch 발행 동안 짧은 DB 트랜잭션을 유지한다.
- PENDING Outbox나 DLQ 메시지가 영구적으로 남으면 자동 종결되지 않으므로 운영 경보와 수동 복구가 필요하다.
- lease 만료 경계에서 `ApplyGuardrail`이 중복 호출되어 비용이 늘 수 있지만 stale 응답은 terminal 결과에 반영되지 않는다.
- 초기 timeout, lease와 retry 값은 실제 Bedrock 지연과 부하를 관찰해 조정해야 한다.

이 결정을 되돌리려면 새 ADR로 메시지와 처리 의미를 supersede한다. 이미 발행된 v1 메시지의 필드 의미를 조용히 바꾸거나 완료된 TestExecution과 QualityGateResult를 덮어쓰지 않는다.

## Validation

1. TestRun 접수와 `TestRunRequested` Outbox가 함께 commit 또는 rollback되는지 검증한다.
2. 같은 `TestRunRequested`를 중복 수신해도 materialization과 fan-out이 중복 확정되지 않는지 검증한다.
3. materialization 성공 뒤 DB commit 실패 시 동일한 client request token으로 복구되는지 검증한다.
4. Candidate version, `RUNNING`과 `2 * Snapshot` 실행 Outbox가 전부 함께 commit되는지 검증한다.
5. 준비 최종 실패에서 모든 실행이 `NOT_STARTED`이고 `FINISHED / ERROR + NOT_EVALUATED`가 원자적으로 저장되는지 검증한다.
6. 같은 TestExecution을 두 Executor가 받아도 같은 시점에는 하나만 유효한 claim을 얻는지 검증한다.
7. Provider 호출 중 lease가 만료되어 새 Worker 호출과 겹쳐도 이전 Worker의 늦은 결과가 저장되지 않고 terminal 결과와 완료 이벤트가 하나로 수렴하는지 검증한다.
8. Provider timeout, retryable 오류와 영구 오류가 승인된 terminal 상태와 안전한 code로 매핑되는지 검증한다.
9. DB와 SQS 오류가 TestExecution 실패로 저장되지 않는지 검증한다.
10. 결과 commit 후 ack 전 장애에서 재전달이 외부 호출과 결과를 중복시키지 않는지 검증한다.
11. Outbox 발행 성공 후 `PUBLISHED` commit 실패에서 같은 `eventId` 재발행이 안전한지 검증한다.
12. 완료 이벤트가 중복·역순·동시에 도착해도 SnapshotEvaluation, 진행률과 QualityGateResult가 하나로 수렴하는지 검증한다.
13. `FINISHED + QualityGateResult` 재호출이 기존 결과를 반환하고 재계산·덮어쓰지 않는지 검증한다.
14. 필수 필드 누락, 잘못된 타입, 알 수 없는 event type과 미지원 schema version이 DLQ로 격리되는지 검증한다.
15. 패키지 의존 테스트가 `testrun -> evaluation`과 Domain의 AWS/JPA 의존을 허용하지 않는지 검증한다.
16. Java, JPA Entity, Migration, 의존성과 공개 API 구현이 이 문서 PR에서 변경되지 않았는지 확인한다.

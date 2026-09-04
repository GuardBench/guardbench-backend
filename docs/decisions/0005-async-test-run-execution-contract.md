# 0005. 비동기 TestRun 실행 계약

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Origin: [GitHub Issue #5](https://github.com/GuardBench/guardbench-backend/issues/5)
> Related: [ADR 0008](0008-async-testrun-persistence-contract.md), [ADR 0010](0010-single-target-test-run-model.md), [ADR 0013](0013-response-behavior-classifier.md)

- ADR Status: ACCEPTED
- Decision date: 2026-08-25
- Related Issue: #5

## Context

TestRun은 HTTP 접수 이후 Snapshot별 AI Application 실행과 Response Behavior Classifier 평가를 비동기로 처리한다. SQS Standard의 중복 전달과 순서 뒤바뀜을 정상 상황으로 취급하고, DB의 claim/outbox/terminal result를 이용해 결과를 하나로 수렴시켜야 한다.

## Decision

### 처리 단계와 Queue

현재 비동기 파이프라인은 세 단계다.

```text
POST /test-runs
  -> TestRun + Snapshot + TestRunRequested Outbox
  -> gb-run-resolve
  -> TestRun resolution + Snapshot 실행 fan-out
  -> gb-workitems
  -> AI Application 실행
  -> Response Behavior Classifier 실행
  -> terminal TestExecution + TestExecutionCompleted Outbox
  -> gb-run-finalize
  -> Assertion / Quality Gate / TestRun FINISHED
```

논리적 역할은 resolution, execution, finalization으로 분리하지만 현재 배포 단위가 반드시 별도 ECS Service일 필요는 없다. 배포 토폴로지는 인프라 계약이 소유한다.

### 메시지 계약

현재 생성하는 비동기 메시지는 schema version 2를 사용한다.

공통 필드:

- `eventId`: UUID
- `eventType`: `TestRunRequested | TestExecutionRequested | TestExecutionCompleted`
- `schemaVersion`: `2`
- `testRunId`: TestRun ID
- `occurredAt`: UTC ISO-8601

Execution 요청/완료 메시지는 `snapshotId`를 추가한다.

```json
{
  "eventId": "0198e2ca-0000-7000-8000-000000000002",
  "eventType": "TestExecutionRequested",
  "schemaVersion": 2,
  "testRunId": 1234,
  "snapshotId": 5678,
  "occurredAt": "2026-09-04T10:00:01Z"
}
```

메시지에는 prompt, ExpectedResult, ApplicationResponse, classifier configuration 전문이나 provider 원문 오류를 복제하지 않는다. Worker는 식별자로 PostgreSQL의 현재 상태를 읽는다.

### Resolution

Resolution worker는 `testRunId` 단위 claim을 획득한다.

- TestRun을 `QUEUED -> PREPARING -> RUNNING`으로 전환한다.
- 현재 HTTP Target은 별도 provider materialization 없이 준비 상태를 확인한다.
- 각 Snapshot에 대해 `TestExecutionRequested` Outbox를 생성한다.
- 논리적 중복 key는 `TestExecutionRequested:{snapshotId}`다.
- 동일 Run의 중복 resolution 메시지는 claim과 현재 상태로 멱등 처리한다.

### Execution

Execution worker의 작업 단위는 Snapshot 하나다.

1. `snapshotId` 단위 execution claim을 획득한다.
2. TestCaseSnapshot prompt로 HTTP AI Application Target을 호출한다.
3. ApplicationResponse와 원래 prompt를 Response Behavior Classifier에 전달한다.
4. classifier 결과를 `ALLOW | BLOCK`으로 정규화한다.
5. terminal `TestExecution`과 `TestExecutionCompleted` Outbox를 원자적으로 저장한다.

외부 HTTP Target 또는 classifier 호출 중 DB transaction/row lock을 유지하지 않는다.

### 실패와 retry

- Provider 호출 오류는 retryable 여부와 시도 횟수를 기준으로 재시도한다.
- retry 소진 시 `FAILED` 또는 `TIMED_OUT` terminal 결과로 수렴한다.
- 실행 전에 처리할 수 없는 경우 `NOT_STARTED`를 사용할 수 있다.
- provider 실패와 DB/SQS 같은 infrastructure 실패를 같은 TestExecution 오류로 취급하지 않는다.
- lease 만료 경계에서 외부 호출이 중복될 수는 있지만 현재 claim token만 terminal 저장을 완료할 수 있다.

### Finalization

Execution 완료 이벤트는 finalization 단계로 수렴한다.

- Snapshot별 저장된 verdict와 ExpectedResult로 Assertion을 계산한다.
- 모든 Snapshot이 terminal이면 TestRun 단위 Quality Gate를 계산한다.
- QualityGateResult 저장과 TestRun `FINISHED` 전환은 하나의 Application transaction에서 처리한다.
- 완료된 Run에 대한 중복 finalization은 기존 최종 상태로 수렴한다.

Regression은 이 실행 파이프라인에서 생성하지 않는다. 별도 조회 흐름에서 동일한 Snapshot 정의와 classifier 조건을 만족하는 과거 완료 Run 결과를 비교한다.

### Source of Truth

- TestRun/Target/Execution/Evaluation의 현재 상태는 PostgreSQL이 source of truth다.
- SQS 메시지는 작업 통지와 식별자 전달 역할만 한다.
- Outbox가 DB commit과 메시지 발행 사이의 원자성 경계를 담당한다.

## Validation

- Snapshot 수와 `TestExecutionRequested` 수가 1:1인지 검증한다.
- 중복 SQS 전달에서도 terminal TestExecution이 하나로 수렴하는지 검증한다.
- execution claim이 유효 lease 동안 중복 선점되지 않는지 검증한다.
- HTTP Application response와 classifier verdict가 성공 execution에 함께 저장되는지 검증한다.
- retry 소진과 timeout이 terminal 상태로 수렴하는지 검증한다.
- finalization 중복 호출이 Quality Gate/TestRun 최종 상태를 중복 생성하지 않는지 검증한다.

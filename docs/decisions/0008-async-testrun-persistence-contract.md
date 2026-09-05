# 0008. 비동기 TestRun 물리 멱등성·claim·Outbox 계약

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Origin: [GitHub Issue #49](https://github.com/GuardBench/guardbench-backend/issues/49)
> Related: [ADR 0002](0002-postgresql-persistence-contract.md), [ADR 0005](0005-async-test-run-execution-contract.md)

- ADR Status: ACCEPTED
- Decision date: 2026-08-25
- Related Issue: #49

## Context

ADR 0005의 비동기 실행을 PostgreSQL에서 안전하게 구현하기 위해 HTTP idempotency, resolution claim, execution claim과 transactional outbox의 물리 계약을 고정한다.

## Decision

### HTTP Idempotency

```text
test_run_idempotency
- idempotency_key     VARCHAR(100) PK
- request_fingerprint CHAR(64) NOT NULL
- test_run_id         BIGINT NOT NULL UNIQUE FK -> test_run(id)
- created_at          TIMESTAMPTZ(6) NOT NULL
- expires_at          TIMESTAMPTZ(6) NOT NULL
```

- 같은 key와 fingerprint면 기존 TestRun을 반환한다.
- 같은 key와 다른 fingerprint면 `409 IDEMPOTENCY_KEY_CONFLICT`다.
- 사용자 지정 Quality Gate threshold는 fingerprint에 포함한다. 기본 0.95/0.95 정책은 배포 시점에 살아 있는 기존 idempotency record와의 호환을 위해 기존 TestSuite/Target fingerprint 형식을 유지한다.
- TTL은 현재 3시간이다.
- 만료 판단은 여러 인스턴스 간 clock drift를 피하기 위해 PostgreSQL `clock_timestamp()`을 사용한다.

### Resolution claim

```text
test_run_resolution_claim
- test_run_id   BIGINT PK FK -> test_run(id)
- claim_token   UUID NOT NULL
- lease_until   TIMESTAMPTZ(6) NOT NULL
- attempt_count INTEGER NOT NULL DEFAULT 0
- claimed_at    TIMESTAMPTZ(6) NOT NULL
- updated_at    TIMESTAMPTZ(6) NOT NULL
```

- claim이 없거나 lease가 만료된 경우에만 원자적으로 새 token을 획득한다.
- lease 시간 비교는 PostgreSQL `clock_timestamp()`을 사용한다.
- 유효한 다른 claim이 있으면 외부 Target 호출이나 fan-out을 중복 수행하지 않는다.

### Execution claim

```text
test_execution_claim
- snapshot_id   BIGINT PK FK -> test_case_snapshot(id)
- claim_token   UUID NOT NULL
- lease_until   TIMESTAMPTZ(6) NOT NULL
- attempt_count INTEGER NOT NULL DEFAULT 0
- claimed_at    TIMESTAMPTZ(6) NOT NULL
- updated_at    TIMESTAMPTZ(6) NOT NULL
```

- Snapshot 하나가 execution 작업 단위다.
- 유효한 lease가 있는 동안 다른 Worker가 같은 Snapshot을 선점하지 못한다.
- 현재 claim token을 소유한 Worker만 최초 terminal TestExecution과 완료 Outbox를 저장한다.
- 최대 실행 시도 수는 Application configuration이 소유하고 DB CHECK로 고정하지 않는다.

### Outbox

```text
outbox_event
- event_id          UUID PK
- event_type        VARCHAR(32) NOT NULL
- schema_version    SMALLINT NOT NULL
- payload           JSONB NOT NULL
- deduplication_key TEXT NOT NULL UNIQUE
- status            VARCHAR(16) NOT NULL
- created_at        TIMESTAMPTZ(6) NOT NULL
- published_at      TIMESTAMPTZ(6)
```

허용 event type:

- `TestRunRequested`
- `TestExecutionRequested`
- `TestExecutionCompleted`

현재 Application이 생성하는 이벤트 payload는 schema version 2다. 현재 persistence validator는 저장된 event record 표현을 위해 version 1과 2를 허용한다.

현재 deduplication key:

- `TestRunRequested:{testRunId}`
- `TestExecutionRequested:{snapshotId}`
- `TestExecutionCompleted:{snapshotId}`

payload에는 prompt, ExpectedResult, ApplicationResponse, classifier configuration 전문 또는 provider 원문 오류를 넣지 않는다.

Publisher는 PENDING event를 batch로 가져와 SQS 발행에 성공한 event만 PUBLISHED로 변경한다. 같은 Outbox event를 재발행해도 `eventId`와 `occurredAt`은 바뀌지 않는다.

### 시간 소유권

- TestRun/Domain lifecycle 시각은 Application이 주입받은 `Clock`을 사용한다.
- claim lease와 idempotency 만료처럼 여러 Worker가 공유하는 동시성 판단은 PostgreSQL DB time을 사용한다.

## Consequences

- HTTP retry, Outbox 재발행, Worker 중복 전달, claim 재선점, finalization 재진입을 서로 다른 멱등성 경계로 다룬다.
- Snapshot execution identity가 단일 `snapshot_id`로 정렬된다.
- SQS 메시지에 실행 입력이나 결과를 복제하지 않아 PostgreSQL을 source of truth로 유지한다.

## Validation

1. 동일 idempotency key의 재요청/충돌/만료 동작을 검증한다.
2. resolution/execution claim이 유효 lease에 중복 선점되지 않는지 검증한다.
3. 만료된 token의 늦은 결과가 terminal 저장을 덮어쓰지 않는지 검증한다.
4. Outbox deduplication key와 PENDING/PUBLISHED shape를 검증한다.
5. 현재 생성되는 schema version 2 payload가 식별자만 포함하는지 검증한다.

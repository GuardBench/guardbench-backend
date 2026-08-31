# 0008. 비동기 TestRun 물리 멱등성·claim·Outbox 계약

> ⚠️ execution claim 복합 key, role 포함 deduplication key, v1 pending payload 부분은 [ADR 0010](0010-single-target-test-run-model.md)이 대체한다.

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-25
> Canonical source: GitHub
> Origin: [GitHub Issue #49](https://github.com/GuardBench/guardbench-backend/issues/49)

- ADR Status: ACCEPTED
- Decision date: 2026-08-25
- Related Issue: #49
- Extends: ADR 0002, ADR 0005
- Superseded in part by: [ADR 0011](0011-ai-application-target-and-guardrail-evaluator.md) — Target 준비·실행 의미. 이 ADR의 claim·Outbox는 current implementation 기록

## Context

ADR 0005는 비동기 실행의 Outbox, execution claim, resolution claim과 HTTP Idempotency 경계를 승인했지만, #14가 Flyway DDL·ERD·Adapter를 만들기 위한 일부 물리 표현을 남겼다. 이 ADR은 해당 경계를 구체화하며 기존 Aggregate·메시지·최종화 의미를 바꾸지 않는다.

## Decision

### HTTP Idempotency

```text
test_run_idempotency
- idempotency_key    VARCHAR(100) PK
- request_fingerprint CHAR(64) NOT NULL
- test_run_id        BIGINT NOT NULL UNIQUE FK -> test_run(id) ON DELETE RESTRICT
- created_at         TIMESTAMPTZ(6) NOT NULL
- expires_at         TIMESTAMPTZ(6) NOT NULL

CHECK (expires_at > created_at)
INDEX (expires_at)
```

- key는 사용자/tenant가 없는 MVP에서 `POST /api/v1/test-runs` 전체 범위로 unique다.
- fingerprint는 정규화된 TestRun create intent의 SHA-256 hex다. raw JSON, Snapshot, materialized version, 실행 시각과 Outbox event는 포함하지 않는다.
- 같은 key와 fingerprint면 기존 TestRun의 현재 상태를 반환하고, fingerprint가 다르면 `409 IDEMPOTENCY_KEY_CONFLICT`다.
- TTL은 3시간이다. `expires_at`은 PostgreSQL `clock_timestamp()`으로 논리 만료를 즉시 판정하며 만료 key는 새 요청에 재사용할 수 있다. 기존 TestRun은 삭제하지 않는다.
- expired row의 batch cleanup은 MVP 후속 운영 범위다. cleanup 지연은 만료 key 재사용을 막지 않는다.

### Resolution claim

```text
test_run_resolution_claim
- test_run_id   BIGINT PK FK -> test_run(id) ON DELETE RESTRICT
- claim_token   UUID NOT NULL
- lease_until   TIMESTAMPTZ(6) NOT NULL
- attempt_count INTEGER NOT NULL DEFAULT 0
- claimed_at    TIMESTAMPTZ(6) NOT NULL
- updated_at    TIMESTAMPTZ(6) NOT NULL

CHECK (attempt_count >= 0)
CHECK (lease_until >= claimed_at)
```

- claim이 없거나 `lease_until <= clock_timestamp()`일 때만 조건부 원자 SQL로 새 token을 선점한다.
- claim lease는 45초, resolution materialization 최대 시도는 3회다. `attempt_count`는 실제 claim을 얻어 materialization을 시도한 횟수만 센다.
- TestRun lifecycle 시각은 Application Clock이 결정하지만, resolution/execution claim의 lease 시각·비교는 PostgreSQL `clock_timestamp()`을 사용한다.
- 유효 claim이 다른 Worker에 있으면 ack·Provider 호출을 하지 않는다. `RUNNING` 또는 `FINISHED` Run의 중복 요청은 materialization 없이 멱등 성공이다.
- 영구 오류 또는 resolution retry 소진 시 모든 실행을 `NOT_STARTED`, TestRun을 `FINISHED / ERROR`, Quality Gate를 `NOT_EVALUATED`로 하나의 Application 트랜잭션에서 종결한다.
- claim row cleanup, status, history relation은 MVP에 추가하지 않는다.

### Execution claim

```text
test_execution_claim
- snapshot_id   BIGINT NOT NULL FK -> test_case_snapshot(id) ON DELETE RESTRICT
- target_type   VARCHAR(16) NOT NULL
- claim_token   UUID NOT NULL
- lease_until   TIMESTAMPTZ(6) NOT NULL
- attempt_count INTEGER NOT NULL DEFAULT 0
- claimed_at    TIMESTAMPTZ(6) NOT NULL
- updated_at    TIMESTAMPTZ(6) NOT NULL

PK (snapshot_id, target_type)
CHECK target_type IN ('BASELINE', 'CANDIDATE')
CHECK (attempt_count >= 0)
CHECK (lease_until >= claimed_at)
```

- claim lease는 45초이고 lease 비교·claim 시각은 `clock_timestamp()`을 쓴다.
- Application은 현재 설정된 최대 실행 시도(초기값 3회)와 `attempt_count`를 비교한다. DB CHECK는 설정값을 고정하지 않는다.
- PK가 claim 조회 경로이므로 추가 index를 두지 않는다. claim은 terminal TestExecution보다 먼저 생길 수 있어 TestExecution FK는 두지 않는다.
- 현재 token만 최초 terminal TestExecution과 `TestExecutionCompleted` Outbox를 같은 트랜잭션으로 저장한다.

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
- published_at      TIMESTAMPTZ NULL

CHECK event_type IN ('TestRunRequested', 'TestExecutionRequested', 'TestExecutionCompleted')
CHECK (schema_version = 1)
CHECK status IN ('PENDING', 'PUBLISHED')
CHECK ((status = 'PENDING' AND published_at IS NULL) OR (status = 'PUBLISHED' AND published_at IS NOT NULL))
INDEX (status, created_at)
```

- `eventType`이 Queue를 결정하므로 destination과 `test_run_id`를 별도 정규 컬럼으로 중복 저장하지 않는다.
- payload는 SQS로 그대로 발행하는 v1 JSON 전체다. `event_id`, `event_type`, `schema_version`은 각각 `eventId`, `eventType`, `schemaVersion` payload 값과 같다. `occurredAt`은 최초 이벤트 생성 시 정하고 재발행에 바꾸지 않는다.
- `TestRunRequested` key는 `TestRunRequested:{testRunId}`다. 실행 요청·완료 key는 각각 `{eventType}:{snapshotId}:{targetType}`다.
- payload에는 입력, ExpectedResult, ActualResult, Guardrail 설정 전문, Provider 원문 오류와 Completed의 executionStatus를 넣지 않는다.
- Publisher는 `SELECT ... FOR UPDATE SKIP LOCKED`로 PENDING batch를 가져오고, SQS 발행 성공 항목만 PUBLISHED로 바꾼다. `PROCESSING`, `DEAD`, attempt, next-attempt는 MVP 범위가 아니다.

## Alternatives

- Application Clock으로 lease를 비교하면 단위 테스트는 단순하지만 여러 ECS Worker의 clock drift가 선점 의미를 바꿀 수 있어 선택하지 않는다.
- raw request JSON을 Idempotency fingerprint로 저장하면 field 순서·공백 차이가 다른 요청이 되므로 선택하지 않는다.
- Idempotency TTL을 cleanup 완료 시점까지 유지하면 cleanup 지연이 API 의미를 바꾸므로 선택하지 않는다.
- Outbox에 destination·test_run_id를 중복 저장하면 payload/라우팅과 별도 정합성 문제가 생겨 선택하지 않는다.

## Consequences

#14는 이 ADR을 기준으로 Flyway Migration, PlantUML ERD, Persistence Entity/Adapter와 Testcontainers 통합 테스트를 구현한다. #16은 Idempotency와 접수+Outbox 원자성을, #18은 resolution/execution claim과 ack/retry를 구현한다. 이 ADR은 실제 코드·Migration을 포함하지 않는다.

## Validation

1. 같은 Idempotency-Key의 동일 fingerprint 재요청, 충돌, 3시간 만료 뒤 재사용을 검증한다.
2. resolution/execution claim이 유효 lease에는 중복 선점되지 않고 만료 token의 늦은 결과가 저장되지 않는지 검증한다.
3. resolution retry 소진이 `NOT_STARTED + FINISHED/ERROR + NOT_EVALUATED`로 원자 종결되는지 검증한다.
4. Outbox unique key, `PENDING/PUBLISHED` shape와 `SKIP LOCKED` batch 발행을 검증한다.
5. event payload가 승인된 v1 JSON만 포함하고 재발행에서 eventId·occurredAt이 바뀌지 않는지 검증한다.

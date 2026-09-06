# 비동기 TestRun 계약 맵

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-31
> Canonical source: GitHub
> Origin: [GitHub Issue #49](https://github.com/GuardBench/guardbench-backend/issues/49)

이 문서는 **current implementation의** 비동기 TestRun 구현을 찾는 결정형 탐색 맵이다. 각 계약 키에는 구현 결론을 정하는 APPROVED `Primary contract`를 하나만 둔다. `보조 참조`는 함께 읽어야 할 인접 계약이지만 Primary의 소유 범위를 대체하지 않는다.

이 문서는 ADR·ERD·Migration을 복사하거나 새 동작·DB 구조를 정하지 않는다. 구현은 항상 Primary contract를 근거로 하며, Primary와 보조 참조가 충돌하거나 필요한 계약 키가 없으면 Issue에 기록하고 중단한다.

Application Target → classifier 실행 계약은 [ADR 0013](../decisions/0013-response-behavior-classifier.md)이 소유한다. #173에서 SageMaker classifier Adapter와 Application response → classifier verdict → Assertion 실행 경계를 구현했으며, 이 맵의 retry·claim·Outbox 계약과 함께 적용한다. Quality Gate는 #118, Regression은 #119 범위다.

각 행의 Primary contract는 현재 Migration, JPA Entity, Worker Service, Application Service와 대조해 확인한다. PostgreSQL 통합 테스트는 접수·멱등성·부분 실패·최종화와 영속성 경계를 검증하며, SQS 전송·DLQ처럼 직접 실행하지 않는 경계는 각 구현 작업의 단위·통합 테스트가 담당한다.

## 결정형 계약 맵

| 계약 키 | Primary contract | 필수 보조 참조 | Primary가 소유하는 결정 | 구현 범위 |
| --- | --- | --- | --- | --- |
| `postgresql-core-schema` | [ADR 0010: 단일 Target 모델](../decisions/0010-single-target-test-run-model.md) | [ADR 0002](../decisions/0002-postgresql-persistence-contract.md), [PlantUML ERD](../diagrams/guardbench-mvp-physical-erd.puml) | V3 Target reference·provider table, 단일 execution/claim PK, 기존 FK·시각 규칙 | #14, #106 |
| `result-write-boundary` | [ADR 0003: 결과 Aggregate와 write-side Port](../decisions/0003-result-aggregate-and-write-port-boundaries.md) | [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md) | TestExecution·SnapshotEvaluation·QualityGateResult의 Aggregate·Repository 소유권 | #14 |
| `testrun-finalization` | [ADR 0004: 최종화 원자성](../decisions/0004-testrun-finalization-atomicity.md) | [ADR 0003](../decisions/0003-result-aggregate-and-write-port-boundaries.md), [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md) | QualityGateResult 저장과 TestRun `FINISHED` 전환의 원자성·재호출 의미 | #14, #18, #19 |
| `current-target-input` | [ADR 0013: Response Behavior Classifier](../decisions/0013-response-behavior-classifier.md) | [TestRun Persistence current implementation](../architecture/testrun-persistence.md), [HTTP Target Adapter](../integrations/http-endpoint-target.md) | 현재 코드의 단일 `HTTP_ENDPOINT` target·identifier·model·optional revision | #115, #125, #128 |
| `testrun-idempotency` | [ADR 0008: HTTP Idempotency](../decisions/0008-async-testrun-persistence-contract.md#http-idempotency) | [ADR 0010](../decisions/0010-single-target-test-run-model.md), [ADR 0002](../decisions/0002-postgresql-persistence-contract.md) | global key, normalized single-target intent SHA-256, 3시간 TTL, DB 논리 만료와 재사용 | #14, #16, #106 |
| `async-message-contract` | [ADR 0010: v2 role-free 메시지](../decisions/0010-single-target-test-run-model.md) | [ADR 0005](../decisions/0005-async-test-run-execution-contract.md), [ADR 0008: Outbox](../decisions/0008-async-testrun-persistence-contract.md#outbox) | v2 JSON, targetType 제거, Queue routing, `{eventType}:{snapshotId}` deduplication | #106 |
| `outbox-persistence` | [ADR 0008: Outbox](../decisions/0008-async-testrun-persistence-contract.md#outbox) | [ADR 0005: Outbox와 Publisher](../decisions/0005-async-test-run-execution-contract.md#outbox와-publisher), [ADR 0002](../decisions/0002-postgresql-persistence-contract.md) | `outbox_event` DDL, payload 저장, deduplication key, `PENDING/PUBLISHED`, `SKIP LOCKED` | #14, #16, #18 |
| `resolution-flow` | [ADR 0013: Response Behavior Classifier](../decisions/0013-response-behavior-classifier.md) | [ADR 0008: Resolution claim](../decisions/0008-async-testrun-persistence-contract.md#resolution-claim), [HTTP Target Adapter](../integrations/http-endpoint-target.md) | HTTP Target reference 검증, Snapshot당 1개 fan-out, `QUEUED → PREPARING → RUNNING` | #115, #125, #128, #117 |
| `resolution-claim-persistence` | [ADR 0008: Resolution claim](../decisions/0008-async-testrun-persistence-contract.md#resolution-claim) | [ADR 0005](../decisions/0005-async-test-run-execution-contract.md#작업-단위와-fan-out) | `test_run_resolution_claim` DDL, 45초 DB lease, 3회 제한, 실패 원자 종결 | #14, #18 |
| `execution-claim-persistence` | [ADR 0008: Execution claim](../decisions/0008-async-testrun-persistence-contract.md#execution-claim) | [ADR 0005: TestExecution claim과 결과 저장](../decisions/0005-async-test-run-execution-contract.md#testexecution-claim과-결과-저장) | `test_execution_claim` DDL, DB lease, stale 결과 차단, terminal 결과·완료 Outbox 원자 저장 | #14, #18, #19 |
| `provider-retry-and-dlq` | [ADR 0010: 단일 Target 실행 모델](../decisions/0010-single-target-test-run-model.md) | [ADR 0005: 재시도·timeout·visibility·DLQ](../decisions/0005-async-test-run-execution-contract.md#재시도-timeout-visibility와-dlq), [SageMaker Classifier Adapter](../integrations/sagemaker-classifier-adapter.md) | Provider retry·timeout·visibility·DLQ와 안전한 TestExecution 오류 수렴 | #106 |
| `execution-error-code` | [애플리케이션 오류 코드: TestExecution 실행 오류 Code](../conventions/application-errors.md#testexecution-실행-오류-code) | [ADR 0010](../decisions/0010-single-target-test-run-model.md), [SageMaker Classifier Adapter](../integrations/sagemaker-classifier-adapter.md) | Application Target/classifier 단계가 포함된 공개 가능한 TestExecution 오류 code·terminal 상태·안전한 message 정책 | #106, #117 |
| `testrun-result-detail-query` | [API 안내](../api/README.md), [OpenAPI](../api/openapi.yaml) | [TestRun Persistence 구현 인덱스](../architecture/testrun-persistence.md), [ADR 0013](../decisions/0013-response-behavior-classifier.md) | FINISHED TestRun의 Snapshot별 저장 Application response 상세 조회, TestRun·Snapshot 소속 검증, 재호출 금지 | #237 |
| `context-boundary` | [ADR 0006: 소비자 소유 Port와 값 기반 계약](../decisions/0006-independent-domain-contract-boundaries.md#소비자-소유-port와-값-기반-계약) | [ADR 0002](../decisions/0002-postgresql-persistence-contract.md), [ADR 0003](../decisions/0003-result-aggregate-and-write-port-boundaries.md) | Context 간 Java Domain 타입 격리와 Integration Adapter 변환 | #14, #17, #18 |

## 운영값 코드 매핑 (ADR 0005 초기값 ↔ 구현 위치)

ADR 0005의 재시도·timeout·lease 초기값이 실제 어떤 configuration key 또는 코드 상수로 구현되었는지 추적한다. 이 표는 새 설정값을 정하지 않으며 기존 코드 위치를 가리키기만 한다.

| ADR 0005 운영값 | 값 | 구현 위치 | 외부화 여부 |
| --- | ---: | --- | --- |
| Provider 호출 전체 timeout | 15초 | SageMaker classifier provider configuration | Configuration key |
| SDK 개별 시도 timeout | 5초 | SageMaker Runtime client configuration | Configuration key |
| SDK 최대 시도 | 3회 | SageMaker Runtime client configuration | Configuration key |
| 최대 Application 실행 시도 | 3회 | `ExecuteTestRunService.MAX_EXECUTION_ATTEMPTS` | ⚠️ Java 상수 (미결정 사항 참고) |
| 최대 Application resolution 시도 | 3회 | `ResolveTestRunService.MAX_RESOLUTION_ATTEMPTS` | ⚠️ Java 상수 (미결정 사항 참고) |
| SQS visibility timeout | 30초 | `guardbench.sqs.polling.visibility-timeout-seconds` (`application.yml`) | Configuration key |
| Execution/resolution claim lease | 45초 | `PostgresExecutionClaimAdapter.LEASE_SECONDS`, `PostgresResolutionClaimAdapter.LEASE_SECONDS` | ⚠️ Java 상수 (미결정 사항 참고) |
| DLQ `maxReceiveCount` | 5회 | SQS Queue RedrivePolicy (인프라 구성 범위, 통합 테스트에서만 5로 설정) | 인프라 구성 범위 |
| 재전달 간격 | 약 5초 | 명시적 설정 없음. SQS Standard visibility timeout 만료 후 자연 재수신에 의존 | 명시적 보장 없음 |

**미결정 사항**: ADR 0005는 위 초기 운영값을 "설정으로 분리한다"고 서술하지만, `MAX_EXECUTION_ATTEMPTS`, `MAX_RESOLUTION_ATTEMPTS`, claim lease 45초는 실제로 `application.yml` Configuration Property가 아닌 Java 상수로 구현되어 있다. `#49`의 Non-Goals가 새 초기 설정값 추가를 금지하므로 이 문서는 코드를 변경하지 않고 현재 상태만 기록한다. 설정 외부화 여부는 관련 Issue에서 별도로 결정한다.

## 구현 시 적용 규칙

1. 변경하려는 산출물에 해당하는 계약 키를 찾고, 그 행의 **Primary contract부터** 읽는다.
2. 같은 변경이 여러 계약 키에 걸리면 모든 Primary contract를 읽되, 각 ADR은 자기 열에 적힌 결정만 소유한다. 예를 들어 Outbox의 **메시지 형태**는 `async-message-contract`, **저장 형태**는 `outbox-persistence`가 소유한다.
3. 실제 적용 DDL은 `src/main/resources/db/migration/`의 Flyway SQL이지만, 이 파일은 구현 산출물이다. 계약은 ADR 0002·0008과 PlantUML ERD에서 확인하고 Migration·ERD·통합 테스트의 일치를 검증한다.
4. 이 표에 없는 결정, 또는 Primary 간 충돌은 이 문서를 해석해 임의로 해결하지 않는다. 관련 Issue에 미결정으로 기록한다.

## 검색 보조어

아래 표현은 계약을 정하는 키가 아니라 탐색 편의를 위한 별칭이다.

| 검색 표현 | 계약 키 |
| --- | --- |
| 현재 Target 요청, `HTTP_ENDPOINT`, OpenAI-compatible model | `current-target-input` |
| Idempotency-Key, fingerprint, TTL, key 재사용 | `testrun-idempotency` |
| event JSON, schemaVersion, SQS, eventType | `async-message-contract` |
| Outbox DDL, deduplication, Publisher, `SKIP LOCKED` | `outbox-persistence` |
| HTTP Target 검증, Snapshot fan-out | `resolution-flow` |
| resolution lease, 45초, 3회, `NOT_STARTED` | `resolution-claim-persistence` |
| execution lease, stale token, terminal 결과 | `execution-claim-persistence` |
| retry, timeout, visibility, DLQ | `provider-retry-and-dlq` |
| TARGET_NOT_FOUND, PROVIDER_TIMEOUT, 안전한 오류 code | `execution-error-code` |

# TestRun Persistence 구현 인덱스

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Target architecture: [ADR 0013](../decisions/0013-response-behavior-classifier.md)

이 문서는 현재 PostgreSQL persistence 구현의 탐색 인덱스다. 현재 구현과 fresh database schema만 설명하며 이전 스키마에 대한 upgrade compatibility는 다루지 않는다.

## 현재 계약

- 신규 TestRun Target은 OpenAI-compatible `HTTP_ENDPOINT`만 허용한다.
- TestRun은 하나의 `TargetReference`와 하나의 `EvaluatorReference`를 고정한다.
- `EvaluatorReference`는 실행 당시 Response Behavior Classifier의 provider/model 식별자를 저장한다.
- Snapshot당 `TestExecution`은 하나다.
- 성공한 execution은 AI Application response와 classifier가 정규화한 `ALLOW | BLOCK` verdict를 저장한다.
- Regression은 완료된 Run들의 Snapshot 정의와 저장 verdict를 조회해 계산하며 Application Target이나 classifier를 재호출하지 않는다.
- `quality_gate_result`는 판정 당시의 각 metric value, threshold, passed를 함께 저장해 과거 Run 조회가 이후 정책 상수 변경의 영향을 받지 않게 한다.

## 물리 스키마

현재 fresh database schema는 `src/main/resources/db/migration/V1__create_guardbench_schema.sql` 하나가 생성한다.

주요 테이블은 다음과 같다.

| 영역 | 테이블 |
| --- | --- |
| Test definition | `test_suite`, `test_case` |
| TestRun | `test_run`, `test_case_snapshot`, `test_execution` |
| Target / classifier | `target_reference`, `http_endpoint_target`, `evaluator_reference` |
| Evaluation | `assertion_result`, `change_result`, `quality_gate_result` |
| Async / concurrency | `test_run_idempotency`, `test_run_resolution_claim`, `test_execution_claim`, `outbox_event` |

편집 가능한 물리 ERD는 [PlantUML ERD](../diagrams/guardbench-mvp-physical-erd.puml)다.

## Target persistence

`target_reference.target_type`은 현재 `HTTP_ENDPOINT`만 허용한다.

`http_endpoint_target`은 다음 값을 저장한다.

- `endpoint_url`: 필수 HTTP/HTTPS URL
- `model`: 필수 OpenAI-compatible model
- `requested_revision`: 선택적 revision metadata

TestRun 상세 조회와 Regression 조회는 `http_endpoint_target`을 직접 JOIN하며 provider fallback 경로를 사용하지 않는다.

## Classifier reference

`evaluator_reference`는 다음 값을 저장한다.

- `provider_code`
- `model_id`

두 값은 nonblank이며 `test_run.evaluator_reference_id`는 필수 FK다. 사용자는 이 값을 TestRun 생성 요청에서 제출하지 않고 서버가 현재 classifier configuration을 등록한다.

## Snapshot과 삭제

- `test_case.test_suite_id`는 현재 편집 자산의 관계이므로 FK를 유지한다.
- `test_case_snapshot.source_test_case_id`와 `test_run.test_suite_id`는 historical identity scalar이며 원본 row에 FK를 두지 않는다.
- TestCase/TestSuite의 현재 정의를 삭제해도 이미 생성된 Snapshot과 TestRun 결과의 의미는 유지된다.

## 비동기 persistence

- HTTP idempotency는 `test_run_idempotency`가 담당한다.
- resolution/execution lease는 각각 `test_run_resolution_claim`, `test_execution_claim`이 담당한다.
- 비동기 이벤트는 `outbox_event`에 저장한다.
- claim lease와 idempotency 만료 비교는 여러 Worker 간 동시성 경계이므로 PostgreSQL DB time을 사용한다.

## 관련 구현

- TestRun write/query adapters: `testrun/infrastructure/persistence`
- HTTP Target persistence: `target/infrastructure/persistence`
- Response Behavior Classifier adapter: `evaluator/infrastructure/sagemaker`
- Evaluation persistence: `evaluation/infrastructure/persistence`
- 스키마 검증: `PersistenceFoundationIntegrationTest` 및 각 persistence integration test

## 운영 전제

이 스키마는 이전 Flyway migration history와의 upgrade compatibility를 제공하지 않는다. 현재 모델을 ground truth로 사용하며, 적용 대상 database는 current V1 schema 기준으로 초기화한다.

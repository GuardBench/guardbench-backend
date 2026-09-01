# TestRun Persistence 구현 인덱스

> Status: APPROVED
> Owner: Backend
> Scope: GitHub Issues #14, #106, #110, #114, #116, #125, #128
> Last reviewed: 2026-09-01
> Target architecture: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)
> Related broad documentation issue: [#49](https://github.com/GuardBench/guardbench-backend/issues/49)

이 문서는 PostgreSQL 물리 Persistence 산출물의 탐색 인덱스다. 새 동작, DB 제약 또는 Context 경계를 결정하지 않으며, 구현 판단은 아래 APPROVED ADR과 현재 Issue를 따른다.

## 계약 층위

이 문서와 물리 ERD는 **current implementation**을 기록한다. 새 TestRun은 OpenAI-compatible `HTTP_ENDPOINT` Application Target과 inline EvaluationProfile을 요구하고, 운영자 catalog가 해석한 immutable Bedrock Guardrail Evaluator reference를 함께 고정한다. 과거 legacy 데이터 보존은 MVP 초기화 환경의 #128 범위에서 고려하지 않는다.

## 승인 계약

- [ADR 0002: PostgreSQL 영속성 계약과 물리 ERD](../decisions/0002-postgresql-persistence-contract.md)
- [ADR 0003: 실행·평가 결과 Aggregate와 write-side Port 경계](../decisions/0003-result-aggregate-and-write-port-boundaries.md)
- [ADR 0006: 독립 Domain 경계와 Java 타입 격리](../decisions/0006-independent-domain-contract-boundaries.md)
- [ADR 0008: 비동기 TestRun 물리 멱등성·claim·Outbox 계약](../decisions/0008-async-testrun-persistence-contract.md)
- [ADR 0010: TestRun 단일 Target 실행 모델](../decisions/0010-single-target-test-run-model.md)
- [ADR 0011: AI Application Target과 Guardrail Evaluator 역할 분리](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)

## 실제 산출물

| 구분 | 위치 | 내용 |
| --- | --- | --- |
| Core schema | `src/main/resources/db/migration/V1__create_guardbench_schema.sql` | TestRun, Snapshot, Execution, Assertion, Change, Quality Gate 테이블·PK/FK/CHECK·index |
| Async technical schema | `src/main/resources/db/migration/V2__create_async_testrun_technical_tables.sql` | HTTP idempotency, Outbox, resolution/execution claim 물리 계약 |
| Single Target schema | `src/main/resources/db/migration/V3__single_target_execution_model.sql` | Target reference/provider table, 단일 execution·claim PK, pending v2 Outbox 이관 |
| HTTP Endpoint Target schema | `src/main/resources/db/migration/V4__http_endpoint_target.sql`, `V7__openai_compatible_http_target.sql`, `V8__require_http_target_model.sql` | `HTTP_ENDPOINT` provider table, OpenAI-compatible `model` 저장 및 `NOT NULL` 제약 |
| HTTP Endpoint URL constraint | `src/main/resources/db/migration/V5__strengthen_http_endpoint_url_constraint.sql` | `endpoint_url`의 HTTP/HTTPS scheme과 host 형태 DB 제약 강화 |
| Evaluator reference and Profile snapshot | `src/main/resources/db/migration/V6__evaluator_reference_and_profile.sql` | Evaluator provider/revision 고정, TestRun profile snapshot 및 HTTP Target revision |
| Application/Evaluator execution result | `src/main/resources/db/migration/V9__separate_application_and_evaluator_results.sql`, `V10__remove_legacy_actual_action.sql` | Application response, Evaluator verdict, 실패 단계와 legacy action column 제거 및 execution shape CHECK 제약 |
| Current-run Quality Gate metrics | `src/main/resources/db/migration/V11__current_run_quality_gate_metrics.sql` | 현재 Run의 Assertion 통과율·실행 성공률과 `NOT_EVALUATED` 저장 shape |
| ERD | [PlantUML ERD](../diagrams/guardbench-mvp-physical-erd.puml) | migration 적용 후 관계와 cardinality |
| TestRun write adapters | `testrun/infrastructure/persistence` | TestRun, Snapshot, TestExecution, idempotency, Outbox, claim Adapter |
| Target/Evaluator adapters | `target/infrastructure/persistence`, `testrun/infrastructure/evaluator`, `evaluator/infrastructure/bedrock` | HTTP Target 등록, operator catalog 해석, immutable EvaluatorReference persistence와 `bedrock_guardrail_evaluator` 조회 |
| Evaluation write adapters | `evaluation/infrastructure/persistence` | Assertion-only SnapshotEvaluation 및 NOT_EVALUATED QualityGateResult Adapter |
| Evaluation write ports | `evaluation/domain/repository` | Evaluation 소유 local reference VO를 쓰는 write-side Port |
| PostgreSQL integration tests | `src/test/java/com/guardbench/*Persistence*IntegrationTest.java`, `EvaluationPersistenceAdapterIntegrationTest.java` | Flyway schema와 Repository round-trip·제약 검증 |

## HTTP Target persistence 계약

`http_endpoint_target.model`은 신규 MVP 데이터에서 필수다. `TargetReferenceReq.model`, Target 등록 값, DB column과 조회 응답이 모두 non-blank/non-null 의미로 정렬된다. MVP는 generic `{"input": ...}` / `{"response": ...}` Target 계약을 지원하지 않으므로 Target 종류를 구분하기 위한 nullable model을 두지 않는다.

## 시각 소유권

- Aggregate 생성·수정·lifecycle 시각은 Application이 주입받은 `Clock`으로 결정하고 Domain과 Persistence Adapter가 `Instant`를 보존한다.
- Evaluation의 `SnapshotEvaluation.createdAt`과 `QualityGateResult.createdAt`은 해당 규칙을 따른다.
- idempotency 만료와 resolution/execution claim lease·비교는 여러 Worker 간 동시성 경계이므로 ADR 0008대로 PostgreSQL `clock_timestamp()`을 사용한다.

## 목표 구조와의 차이

#114의 profile/evaluator snapshot과 #115/#125/#128의 OpenAI-compatible HTTP Application Target 경계가 구현되어 있다. #116에서 Bedrock Guardrail Evaluator Adapter가 추가되었고, #117에서 Worker가 Application response → Evaluator verdict → Assertion 경계를 사용해 결과를 저장·조회한다. Application response는 내부 저장 값이며 public 결과에는 노출하지 않는다.

#119의 Regression은 별도 결과를 저장하지 않고 완료된 두 Run의 Snapshot 정의와 저장 verdict를 읽어 조회 시 계산한다. 고정 Evaluator provider/identifier/revision은 비교 후보 필터에 사용하며 Application Target/Evaluator 재호출은 없다.

Quality Gate는 #118에서 현재 Run의 평가 가능한 Assertion 통과율과 전체 실행 성공률을 저장하며, Regression 저장/API는 #119의 범위다.

## 범위 제외

- QualityGateResult 저장과 TestRun `FINISHED` 전환의 Application 트랜잭션 조율은 ADR 0004 및 후속 Worker/finalization 범위다.
- Queue, retry, DLQ, Worker 계약은 [ADR 0005](../decisions/0005-async-test-run-execution-contract.md)와 [비동기 TestRun 계약 맵](../contracts/README.md)이 소유하며 이 문서로 대체하지 않는다.
- #19(통합·회귀 테스트)는 실제 PostgreSQL에서 접수·Worker 체인·결과 조회를 검증한다. SQS 전송·DLQ와 같은 비동기 인프라 경계는 이 Persistence 인덱스의 범위가 아니다.

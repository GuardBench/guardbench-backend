# 0010. TestRun 단일 Target 실행 모델

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Origin: GitHub Issue #106
>
> ⚠️ Target 종류와 evaluator 실행 방식은 [ADR 0013](0013-response-behavior-classifier.md)이 대체한다. 현재 신규 TestRun은 OpenAI-compatible `HTTP_ENDPOINT`만 허용하며, 이 문서의 과거 `BEDROCK_GUARDRAIL`/DRAFT 설명은 역사적 결정이다.

- ADR Status: ACCEPTED
- Decision date: 2026-08-30
- Related Issue: #106
- Supersedes: ADR 0002·0003·0005·0007·0008의 Baseline/Candidate, Candidate-only DRAFT, 복합 execution/claim key, v1 role 메시지 부분
- Superseded in part by: [ADR 0013](0013-response-behavior-classifier.md) — AI Application Target, Response Behavior Classifier, Quality Gate와 Regression 역할

## Context

기존 TestRun은 하나의 요청에 Baseline과 Candidate를 함께 보유하고 Snapshot당 두 번 실행했다. 이 구조는 provider-specific evaluator를 상위 도메인으로 드러내고, TestRun의 실행 계약을 비교 용어와 provider revision lifecycle에 결합했다. 다른 Target 구현을 추가하려면 TestRun Domain·DB·API·메시지를 모두 변경해야 하는 문제가 있었다.

## Decision

1. TestRun은 소스 TestSuite와 단일 `TargetReference`만 보유한다. TargetReference는 TestRun Context가 소유하는 불투명 ID VO이며 provider 값을 노출하지 않는다.
2. provider별 상세 저장 값은 Target Context가 소유한다. **현재 신규 Target 계약은 ADR 0013에 따라 `HTTP_ENDPOINT`만 허용한다.**
3. TestRun Application은 provider-independent `TargetPreparationPort`와 `TargetExecutionPort` 계약을 소유한다. 각 provider Adapter만 Target 저장 값을 실행 요청으로 변환한다.
4. Snapshot당 TestExecution은 하나다. `test_execution`과 `test_execution_claim`의 PK는 `snapshot_id`이고, 진행도와 성공 건수는 `testCaseCount`를 기준으로 계산한다.
5. `TestExecutionRequested`와 `TestExecutionCompleted` v2 payload는 `targetType`을 포함하지 않는다. deduplication key는 `{eventType}:{snapshotId}`다. Publisher가 읽을 수 있도록 이미 저장된 v1은 허용하지만 Worker codec은 v2만 처리한다.
6. ~~BEDROCK_GUARDRAIL Target의 DRAFT/numbered revision lifecycle~~은 ADR 0013에 의해 현재 실행 계약에서 제거되었다. 관련 스키마와 migration은 역사 기록 또는 호환 목적으로만 남을 수 있다.
7. Expected/Actual Assertion은 유지하지만 TestRun 실행 중 ChangeResult·comparison·regression은 생성하지 않는다. Regression은 완료된 Run 결과를 조회 시 비교하는 별도 흐름으로 다룬다.
8. resolution/execution claim, provider retry, stale claim 차단, terminal 결과와 Outbox의 원자 저장, TestRun 최종화 트랜잭션은 기존 보장을 유지한다.

## Alternatives

- Baseline/Candidate 모델을 두고 provider만 일반화하는 방안은 TestRun의 복수 실행과 비교 결합을 유지하므로 선택하지 않았다.
- provider type·revision을 TestRun에 직접 저장하는 방안은 Context 경계를 넘어 Target lifecycle을 복제하므로 선택하지 않았다.
- 새 비교 Aggregate나 Quality Gate 정책을 함께 도입하는 방안은 #106 범위가 아니므로 보류한다.

## Consequences

- 실행 회수·Outbox·claim·저장 행이 Snapshot 수와 같아진다.
- 현재 신규 실행은 ADR 0013의 `HTTP_ENDPOINT` Application Target과 Response Behavior Classifier 계약을 따른다.
- 기존 Baseline/Candidate API와 v1 Worker payload는 호환되지 않는다. Flyway V3가 기존 Candidate 대상과 실행을 단일 Target으로 이관하고 pending v1 Outbox를 v2로 변환한다.
- 기존 `change_result` 행과 비교 metrics는 V3에서 제거/초기화됐으며, 현재 Regression은 별도 저장 없이 호환 가능한 완료 Run 결과를 읽어 계산한다.

## Validation

- Domain/Application 단위 테스트로 Snapshot당 하나의 execution, `testCaseCount` 기준 진행도, Assertion-only 평가를 검증한다.
- PostgreSQL 16 통합 테스트로 Flyway migration, 단일 PK/claim, Target FK, 조회 Projection을 검증한다.
- 신규 TestRun 생성은 `HTTP_ENDPOINT` 외 target type을 거부한다.
- Codec·Outbox·SQS 테스트로 v2 role-free payload과 deduplication key를 검증한다.
- `./gradlew clean check bootJar --no-daemon`으로 전체 계약을 검증한다.

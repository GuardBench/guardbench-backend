# 0012. TestSuite/TestCase 영구 삭제와 historical identity

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-03
> Canonical source: GitHub
> Origin: [GitHub Issue #171](https://github.com/GuardBench/guardbench-backend/issues/171)

- ADR Status: ACCEPTED
- Decision date: 2026-09-03
- Related Issue: #171
- Supersedes: [ADR 0009](0009-testcase-soft-delete-concurrency.md)의 TestCase 삭제 정책
- Extends: [ADR 0002](0002-postgresql-persistence-contract.md)

## Context

TestRun 접수 시 TestCase 정의를 Snapshot으로 복제하므로 현재 TestCase와 과거 실행 데이터의 수명은
분리되어 있다. 회귀 비교도 원본 행이 아니라 Snapshot의 정의와 저장된 결과를 사용한다. 따라서 현재
편집 자산의 삭제를 soft delete로 유지할 필요가 없고, TestSuite 삭제 API도 함께 제공할 수 있다.

## Decision

- `DELETE /api/v1/test-cases/{testCaseId}`는 TestCase 행을 물리 삭제한다.
- `DELETE /api/v1/test-suites/{suiteId}`는 Suite와 현재 소속 TestCase를 같은 트랜잭션에서 물리 삭제한다.
- 존재하지 않는 삭제 대상은 `TEST_CASE_NOT_FOUND` 또는 `TEST_SUITE_NOT_FOUND`로 응답하고 성공은 `204`다.
- `test_case.deleted_at`과 soft-delete 조건·Domain·Repository 로직을 제거한다.
- `test_case_snapshot.source_test_case_id`와 `test_run.test_suite_id`는 historical identity scalar 값으로
  유지하며 현재 `test_case`·`test_suite` 행을 참조하는 FK는 제거한다.
- TestRun, Snapshot, Execution, Evaluation, Assertion, Change와 Quality Gate 결과는 원본 삭제 시에도
  삭제하지 않는다.
- PostgreSQL sequence는 삭제 후에도 값을 재사용하지 않으므로 historical identity와 새 자산의 충돌은
  발생하지 않는다.

TestSuite 삭제의 순서는 TestCase를 먼저 삭제하고 TestSuite를 삭제하는 Application Service가 조율한다.
두 Aggregate는 객체 참조나 가변 컬렉션으로 결합하지 않는다.

## Physical migration

V12는 기존 V1을 수정하지 않고 다음을 roll-forward한다.

- `test_case.deleted_at`과 삭제 전용 partial index 제거
- Snapshot source TestCase FK 제거
- TestRun TestSuite FK 제거

TestCase와 TestSuite의 소속 FK는 현재 자산 삭제 순서를 Application Service가 보장하므로 유지한다.

## Consequences

- 현재 편집 목록과 단건 조회에는 soft-delete 조건이 필요하지 않다.
- 과거 Run은 원본 Suite/TestCase가 없어도 Snapshot의 정의와 historical identity로 조회·회귀 비교할 수 있다.
- 원본 자산 복구 기능은 제공하지 않는다.
- 과거 데이터 보존 정책과 TestRun/Snapshot 자체의 운영 삭제는 이 결정의 범위가 아니다.

## Validation

1. TestCase 삭제 후 `test_case` 행이 없어지고 Snapshot·결과 행은 유지되는지 확인한다.
2. TestSuite 삭제 후 Suite와 소속 TestCase 행이 없어지는지 확인한다.
3. 원본 삭제 후에도 `source_test_case_id`, `test_suite_id` 값과 stored-result Regression 비교가 유지되는지 확인한다.
4. V12 migration으로 삭제된 컬럼·FK가 제거되고 현재 조회·Snapshot 생성에 soft-delete 조건이 없는지 확인한다.

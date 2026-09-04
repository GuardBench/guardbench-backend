package com.guardbench.testrun.presentation.dto;

/**
 * 실행 당시 Snapshot과 그 Snapshot의 Application 실행·Evaluator·Assertion 결과다. 현재
 * TestCase 수정·삭제와 무관하게 값이 유지된다. Evaluator verdict가 없으면
 * {@code assertionStatus}와 {@code evaluationOutcome}은 {@code null}이다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestRunResultListItemRes</a>
 */
public record TestRunResultListItemRes(
        long testCaseSnapshotId,
        String name,
        String input,
        String expectedAction,
        String severity,
        String category,
        String executionStatus,
        String evaluatorVerdict,
        String assertionStatus,
        String evaluationOutcome,
        String attentionType,
        ExecutionErrorDetailRes error) {
}

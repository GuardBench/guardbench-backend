package com.guardbench.testrun.presentation.dto;

/**
 * 실행 당시 Snapshot과 그 Snapshot의 Baseline/Candidate 결과다. 현재 TestCase 수정·논리 삭제와 무관하게
 * 값이 유지된다. Candidate ActualResult가 없으면 {@code assertionStatus}는 {@code null}이다.
 * {@code ChangeResult}가 없으면 {@code comparabilityStatus}와 {@code changeType}이 {@code null}이다.
 * {@code NOT_COMPARABLE}이면 {@code changeType}은 {@code null}이다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestRunResultListItemRes</a>
 */
public record TestRunResultListItemRes(
        long snapshotId,
        long testCaseId,
        String name,
        String input,
        String expectedAction,
        String severity,
        String category,
        TestExecutionResultRes baselineExecution,
        TestExecutionResultRes candidateExecution,
        String assertionStatus,
        String comparabilityStatus,
        String changeType) {
}

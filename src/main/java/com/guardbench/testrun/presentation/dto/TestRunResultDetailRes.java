package com.guardbench.testrun.presentation.dto;

/**
 * 실행 당시 Snapshot과 저장된 Application response를 포함한 개별 결과 상세다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestRunResultDetailRes</a>
 */
public record TestRunResultDetailRes(
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
        ExecutionErrorDetailRes error,
        String applicationResponse) {
}

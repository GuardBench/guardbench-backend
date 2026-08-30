package com.guardbench.testrun.presentation.dto;

/**
 * 실행 당시 Snapshot과 그 Snapshot의 단일 Target 결과다. 현재 TestCase 수정·논리 삭제와 무관하게
 * 값이 유지된다. ActualResult가 없으면 {@code assertionStatus}는 {@code null}이다.
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
        TestExecutionResultRes execution,
        String assertionStatus) {
}

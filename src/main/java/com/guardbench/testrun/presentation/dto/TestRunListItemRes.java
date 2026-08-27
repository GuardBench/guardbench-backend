package com.guardbench.testrun.presentation.dto;

/**
 * 실행 이력 목록용 요약이다. Target version과 Quality Gate metrics는 포함하지 않는다.
 * {@code executionOutcome}과 {@code qualityGateStatus}는 아직 결정되지 않았으면 {@code null}이다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestRunListItemRes</a>
 */
public record TestRunListItemRes(
        long id,
        long testSuiteId,
        String status,
        int testCaseCount,
        TestRunProgressRes progress,
        String executionOutcome,
        String qualityGateStatus,
        String createdAt,
        String startedAt,
        String completedAt,
        String updatedAt) {
}

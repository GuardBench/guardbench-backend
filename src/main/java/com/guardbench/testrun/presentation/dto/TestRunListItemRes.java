package com.guardbench.testrun.presentation.dto;

/**
 * 실행 이력 목록용 요약이다. Target version은 포함하지 않는다.
 * {@code executionOutcome}, {@code qualityGateStatus}, {@code qualityGateMetrics}는 아직 결정되지 않았으면
 * {@code null}이다. Quality Gate가 {@code NOT_EVALUATED}이면 metrics도 {@code null}이다.
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
        QualityGateMetricsRes qualityGateMetrics,
        String createdAt,
        String startedAt,
        String completedAt,
        String updatedAt) {
}

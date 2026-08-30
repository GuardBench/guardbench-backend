package com.guardbench.testrun.presentation.dto;

/**
 * Polling을 위한 상태·진행률·요약 결과다. TestCase별 개별 결과 배열은 포함하지 않는다.
 * {@code executionOutcome}과 {@code qualityGate}는 아직 결정되지 않았으면 {@code null}이다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestRunDetailRes</a>
 */
public record TestRunDetailRes(
        long id,
        long testSuiteId,
        String status,
        int testCaseCount,
        TestRunProgressRes progress,
        TargetReferenceRes target,
        String executionOutcome,
        QualityGateRes qualityGate,
        String createdAt,
        String startedAt,
        String completedAt,
        String updatedAt) {
}

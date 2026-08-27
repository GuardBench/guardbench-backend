package com.guardbench.testrun.presentation.dto;

/**
 * {@code resolvedVersion}은 Materialization 완료 전 또는 실패 시 {@code null}이다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - CandidateExecutionTargetRes</a>
 */
public record CandidateExecutionTargetRes(
        String guardrailId,
        String requestedSource,
        String resolvedVersion) {
}

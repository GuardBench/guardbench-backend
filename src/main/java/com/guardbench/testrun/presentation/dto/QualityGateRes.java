package com.guardbench.testrun.presentation.dto;

/**
 * {@code status}가 {@code NOT_EVALUATED}이면 {@code metrics}는 {@code null}이고 {@code PASS} 또는
 * {@code FAIL}이면 전체 {@code metrics}를 제공한다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - QualityGateRes</a>
 */
public record QualityGateRes(String status, QualityGateMetricsRes metrics) {
}

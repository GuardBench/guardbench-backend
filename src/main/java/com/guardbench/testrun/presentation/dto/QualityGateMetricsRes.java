package com.guardbench.testrun.presentation.dto;

/**
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - QualityGateMetricsRes</a>
 */
public record QualityGateMetricsRes(
        double assertionPassRate,
        double executionSuccessRate) {
}

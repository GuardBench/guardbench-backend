package com.guardbench.testrun.presentation.dto;

/**
 * 한 TestRun의 저장된 ExpectedResult와 Evaluator verdict 분류 집계다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - EvaluatorMetricsRes</a>
 */
public record EvaluatorMetricsRes(
        long truePositive,
        long trueNegative,
        long falsePositive,
        long falseNegative,
        Double falsePositiveRate,
        Double falseNegativeRate) {
}

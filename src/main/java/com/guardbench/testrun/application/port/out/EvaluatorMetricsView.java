package com.guardbench.testrun.application.port.out;

public record EvaluatorMetricsView(
        long truePositive,
        long trueNegative,
        long falsePositive,
        long falseNegative,
        Double falsePositiveRate,
        Double falseNegativeRate) {

    public EvaluatorMetricsView {
        if (truePositive < 0 || trueNegative < 0 || falsePositive < 0 || falseNegative < 0) {
            throw new IllegalArgumentException("Evaluator metrics counts must not be negative");
        }
        validateRate(falsePositiveRate, "falsePositiveRate");
        validateRate(falseNegativeRate, "falseNegativeRate");
    }

    private static void validateRate(Double rate, String field) {
        if (rate != null && (rate < 0.0 || rate > 1.0)) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }
}

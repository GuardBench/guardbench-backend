package com.guardbench.evaluation.domain;

public record QualityGateMetrics(
        double assertionPassRate,
        double executionSuccessRate) {

    public QualityGateMetrics {
        requireRate(assertionPassRate, "Assertion pass rate");
        requireRate(executionSuccessRate, "Execution success rate");
    }

    private static void requireRate(double rate, String label) {
        if (!Double.isFinite(rate) || rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException(label + " must be between 0 and 1");
        }
    }
}

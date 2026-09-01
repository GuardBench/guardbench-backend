package com.guardbench.testrun.application.port.out;

public record QualityGateMetricsView(
        double assertionPassRate,
        double executionSuccessRate) {
    public QualityGateMetricsView {
        if (!isRate(assertionPassRate) || !isRate(executionSuccessRate)) {
            throw new IllegalArgumentException("invalid Quality Gate metrics");
        }
    }

    private static boolean isRate(double value) {
        return value >= 0.0 && value <= 1.0;
    }
}

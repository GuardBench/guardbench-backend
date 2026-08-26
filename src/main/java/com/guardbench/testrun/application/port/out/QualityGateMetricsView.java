package com.guardbench.testrun.application.port.out;

public record QualityGateMetricsView(
        double candidateAssertionPassRate,
        long securityRegressionCount,
        double securityRegressionRate,
        double usabilityRegressionRate,
        double testExecutionSuccessRate) {
    public QualityGateMetricsView {
        if (!isRate(candidateAssertionPassRate)
                || securityRegressionCount < 0
                || !isRate(securityRegressionRate)
                || !isRate(usabilityRegressionRate)
                || !isRate(testExecutionSuccessRate)) {
            throw new IllegalArgumentException("invalid Quality Gate metrics");
        }
    }

    private static boolean isRate(double value) {
        return value >= 0.0 && value <= 1.0;
    }
}

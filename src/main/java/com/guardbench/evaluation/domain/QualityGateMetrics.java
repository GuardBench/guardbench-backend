package com.guardbench.evaluation.domain;

public record QualityGateMetrics(
        double candidateAssertionPassRate,
        long securityRegressionCount,
        double securityRegressionRate,
        double usabilityRegressionRate,
        double testExecutionSuccessRate) {

    public QualityGateMetrics {
        requireRate(candidateAssertionPassRate, "Candidate assertion pass rate");
        if (securityRegressionCount < 0) {
            throw new IllegalArgumentException("Security regression count must not be negative");
        }
        requireRate(securityRegressionRate, "Security regression rate");
        requireRate(usabilityRegressionRate, "Usability regression rate");
        requireRate(testExecutionSuccessRate, "Test execution success rate");
    }

    private static void requireRate(double rate, String label) {
        if (!Double.isFinite(rate) || rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException(label + " must be between 0 and 1");
        }
    }
}

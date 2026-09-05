package com.guardbench.testrun.application.port.out;

public record QualityGateMetricView(
        double value,
        double threshold,
        boolean passed) {

    public QualityGateMetricView {
        if (!isRate(value) || !isRate(threshold)) {
            throw new IllegalArgumentException("invalid Quality Gate metric evidence");
        }
        if (passed != (value >= threshold)) {
            throw new IllegalArgumentException("Quality Gate metric decision does not match its evidence");
        }
    }

    private static boolean isRate(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}

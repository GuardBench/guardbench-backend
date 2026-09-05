package com.guardbench.testrun.application.port.out;

public record QualityGateMetricView(
        double value,
        double threshold,
        boolean passed) {

    public QualityGateMetricView {
        if (!isRate(value) || !isRate(threshold)) {
            throw new IllegalArgumentException("invalid Quality Gate metric evidence");
        }
    }

    private static boolean isRate(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}

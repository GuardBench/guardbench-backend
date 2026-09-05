package com.guardbench.evaluation.domain;

/** Quality Gate 판정에 사용된 단일 지표의 값과 기준, 판정 결과다. */
public record QualityGateMetric(
        double value,
        double threshold,
        boolean passed) {

    public QualityGateMetric {
        requireRate(value, "Quality Gate metric value");
        requireRate(threshold, "Quality Gate metric threshold");
        if (passed != (value >= threshold)) {
            throw new IllegalArgumentException("Quality Gate metric decision must match value and threshold");
        }
    }

    public static QualityGateMetric evaluate(double value, double threshold) {
        return new QualityGateMetric(value, threshold, value >= threshold);
    }

    private static void requireRate(double rate, String label) {
        if (!Double.isFinite(rate) || rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException(label + " must be between 0 and 1");
        }
    }
}

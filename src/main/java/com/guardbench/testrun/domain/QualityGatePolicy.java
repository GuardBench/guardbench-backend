package com.guardbench.testrun.domain;

/** TestRun 생성 시 고정되는 Quality Gate 기준값 스냅샷이다. */
public record QualityGatePolicy(
        double assertionPassRateThreshold,
        double executionSuccessRateThreshold) {

    public static final double DEFAULT_ASSERTION_PASS_RATE_THRESHOLD = 0.95;
    public static final double DEFAULT_EXECUTION_SUCCESS_RATE_THRESHOLD = 0.95;

    public QualityGatePolicy {
        requireRate(assertionPassRateThreshold, "Assertion pass rate threshold");
        requireRate(executionSuccessRateThreshold, "Execution success rate threshold");
    }

    public static QualityGatePolicy defaultPolicy() {
        return new QualityGatePolicy(
                DEFAULT_ASSERTION_PASS_RATE_THRESHOLD,
                DEFAULT_EXECUTION_SUCCESS_RATE_THRESHOLD);
    }

    private static void requireRate(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(label + " must be between 0 and 1");
        }
    }
}

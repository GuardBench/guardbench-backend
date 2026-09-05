package com.guardbench.testrun.application.port.out;

public record QualityGateView(String statusCode, QualityGateMetricsView metrics) {
    public QualityGateView {
        TestRunListCriteria.validateQualityGateStatusCode(statusCode);
        if ("NOT_EVALUATED".equals(statusCode) && metrics != null) {
            throw new IllegalArgumentException("NOT_EVALUATED Quality Gate cannot have metrics");
        }
        if (!"NOT_EVALUATED".equals(statusCode) && metrics == null) {
            throw new IllegalArgumentException("evaluated Quality Gate requires metrics");
        }
        if (metrics != null) {
            String metricStatus = metrics.assertion().passed() && metrics.execution().passed()
                    ? "PASS" : "FAIL";
            if (!statusCode.equals(metricStatus)) {
                throw new IllegalArgumentException("Quality Gate status must match metric decisions");
            }
        }
    }
}

package com.guardbench.testrun.application.port.out;

import java.util.Objects;

public record QualityGateMetricsView(
        QualityGateMetricView assertion,
        QualityGateMetricView execution) {

    public QualityGateMetricsView {
        Objects.requireNonNull(assertion, "assertion Quality Gate metric must not be null");
        Objects.requireNonNull(execution, "execution Quality Gate metric must not be null");
    }

    public double assertionPassRate() {
        return assertion.value();
    }

    public double executionSuccessRate() {
        return execution.value();
    }
}

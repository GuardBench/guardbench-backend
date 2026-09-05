package com.guardbench.evaluation.domain;

import java.util.Objects;

public record QualityGateMetrics(
        QualityGateMetric assertion,
        QualityGateMetric execution) {

    public QualityGateMetrics {
        Objects.requireNonNull(assertion, "Assertion Quality Gate metric must not be null");
        Objects.requireNonNull(execution, "Execution Quality Gate metric must not be null");
    }

    public double assertionPassRate() {
        return assertion.value();
    }

    public double executionSuccessRate() {
        return execution.value();
    }
}

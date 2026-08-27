package com.guardbench.evaluation.domain;

import java.time.Instant;
import java.util.Objects;

public record QualityGateResult(
        TestRunEvaluationReference reference,
        QualityGateStatus status,
        QualityGateMetrics metrics,
        Instant createdAt) {

    public QualityGateResult {
        Objects.requireNonNull(reference, "TestRun evaluation reference must not be null");
        Objects.requireNonNull(status, "Quality Gate status must not be null");
        Objects.requireNonNull(createdAt, "Quality Gate createdAt must not be null");
        if (status == QualityGateStatus.NOT_EVALUATED && metrics != null) {
            throw new IllegalArgumentException("Not evaluated Quality Gate cannot have metrics");
        }
        if (status != QualityGateStatus.NOT_EVALUATED && metrics == null) {
            throw new IllegalArgumentException("Evaluated Quality Gate requires metrics");
        }
    }
}

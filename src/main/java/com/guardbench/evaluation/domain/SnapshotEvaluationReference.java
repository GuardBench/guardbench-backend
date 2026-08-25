package com.guardbench.evaluation.domain;

public record SnapshotEvaluationReference(long value) {

    public SnapshotEvaluationReference {
        if (value <= 0) {
            throw new IllegalArgumentException("Snapshot evaluation reference must be positive");
        }
    }
}

package com.guardbench.evaluation.domain;

import java.util.Objects;

public record SnapshotEvaluation(
        SnapshotEvaluationReference reference,
        AssertionResult assertionResult,
        ChangeResult changeResult) {

    public SnapshotEvaluation {
        Objects.requireNonNull(reference, "Snapshot evaluation reference must not be null");
        Objects.requireNonNull(assertionResult, "Assertion result must not be null");
    }
}

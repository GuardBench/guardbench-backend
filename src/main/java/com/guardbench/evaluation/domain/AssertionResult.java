package com.guardbench.evaluation.domain;

import java.util.Objects;

public record AssertionResult(AssertionStatus status) {

    public AssertionResult {
        Objects.requireNonNull(status, "Assertion status must not be null");
    }

    static AssertionResult evaluate(
            EvaluationAction expectedAction,
            EvaluationAction candidateAction) {
        Objects.requireNonNull(expectedAction, "Expected action must not be null");
        Objects.requireNonNull(candidateAction, "Candidate action must not be null");

        return new AssertionResult(expectedAction == candidateAction
                ? AssertionStatus.PASS
                : AssertionStatus.FAIL);
    }
}

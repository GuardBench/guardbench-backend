package com.guardbench.evaluation.domain;

import java.util.Objects;

public record AssertionResult(AssertionStatus status) {

    public AssertionResult {
        Objects.requireNonNull(status, "Assertion status must not be null");
    }

    static AssertionResult evaluate(
            EvaluationAction expectedAction,
            EvaluationAction actualAction) {
        Objects.requireNonNull(expectedAction, "Expected action must not be null");
        Objects.requireNonNull(actualAction, "Actual action must not be null");

        return new AssertionResult(expectedAction == actualAction
                ? AssertionStatus.PASS
                : AssertionStatus.FAIL);
    }
}

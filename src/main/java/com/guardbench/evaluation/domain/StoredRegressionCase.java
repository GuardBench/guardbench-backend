package com.guardbench.evaluation.domain;

import java.util.Objects;

/** 저장된 두 Run의 동일한 TestCase 정의에 대한 Evaluator verdict 쌍이다. */
public record StoredRegressionCase(
        long testCaseId,
        EvaluationAction expectedAction,
        EvaluationAction comparisonVerdict,
        EvaluationAction currentVerdict) {

    public StoredRegressionCase {
        if (testCaseId <= 0) {
            throw new IllegalArgumentException("TestCase ID must be positive");
        }
        Objects.requireNonNull(expectedAction, "Expected action must not be null");
    }
}

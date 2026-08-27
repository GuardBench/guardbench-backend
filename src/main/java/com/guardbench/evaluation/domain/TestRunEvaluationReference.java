package com.guardbench.evaluation.domain;

public record TestRunEvaluationReference(long value) {

    public TestRunEvaluationReference {
        if (value <= 0) {
            throw new IllegalArgumentException("TestRun evaluation reference must be positive");
        }
    }
}

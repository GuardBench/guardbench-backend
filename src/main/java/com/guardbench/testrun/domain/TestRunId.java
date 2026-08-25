package com.guardbench.testrun.domain;

public record TestRunId(long value) {

    public TestRunId {
        if (value <= 0) {
            throw new IllegalArgumentException("TestRun ID must be positive");
        }
    }
}

package com.guardbench.testdefinition.domain;

public record TestCaseId(long value) {

    public TestCaseId {
        if (value <= 0) {
            throw new IllegalArgumentException("TestCaseId must be positive");
        }
    }
}

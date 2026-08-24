package com.guardbench.testdefinition.domain;

public record TestSuiteId(long value) {

    public TestSuiteId {
        if (value <= 0) {
            throw new IllegalArgumentException("TestSuiteId must be positive");
        }
    }
}

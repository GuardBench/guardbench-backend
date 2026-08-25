package com.guardbench.testrun.domain;

public record SourceTestSuiteId(long value) {

    public SourceTestSuiteId {
        if (value <= 0) {
            throw new IllegalArgumentException("source TestSuite ID must be positive");
        }
    }
}

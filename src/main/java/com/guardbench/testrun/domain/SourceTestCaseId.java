package com.guardbench.testrun.domain;

public record SourceTestCaseId(long value) {

    public SourceTestCaseId {
        if (value <= 0) {
            throw new IllegalArgumentException("source TestCase ID must be positive");
        }
    }
}

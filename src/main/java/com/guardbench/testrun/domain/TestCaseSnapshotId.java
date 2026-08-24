package com.guardbench.testrun.domain;

public record TestCaseSnapshotId(long value) {

    public TestCaseSnapshotId {
        if (value <= 0) {
            throw new IllegalArgumentException("TestCaseSnapshotId must be positive");
        }
    }
}

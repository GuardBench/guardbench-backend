package com.guardbench.testrun.domain;

import java.util.Objects;

public record TestExecutionId(TestCaseSnapshotId snapshotId) {

    public TestExecutionId {
        Objects.requireNonNull(snapshotId, "snapshot ID must not be null");
    }
}

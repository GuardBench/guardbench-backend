package com.guardbench.testrun.domain;

import java.util.Objects;

public record TestExecutionId(TestCaseSnapshotId snapshotId, TargetType targetType) {

    public TestExecutionId {
        Objects.requireNonNull(snapshotId, "snapshot ID must not be null");
        Objects.requireNonNull(targetType, "target type must not be null");
    }
}

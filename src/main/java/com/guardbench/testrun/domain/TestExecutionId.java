package com.guardbench.testrun.domain;

import java.util.Objects;

public record TestExecutionId(TestCaseSnapshotId snapshotId, TargetType targetType) {

    public TestExecutionId {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
    }
}

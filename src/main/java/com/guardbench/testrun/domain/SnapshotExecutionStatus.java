package com.guardbench.testrun.domain;

import java.util.Objects;

/** Snapshot의 단일 Target 실행 상태다. */
public record SnapshotExecutionStatus(
        TestCaseSnapshotId snapshotId,
        TestExecutionStatus executionStatus
) {

    public SnapshotExecutionStatus {
        Objects.requireNonNull(snapshotId, "snapshot ID must not be null");
    }

    public boolean isProcessed() {
        return executionStatus != null && executionStatus.isTerminal();
    }

    public boolean succeeded() {
        return executionStatus == TestExecutionStatus.SUCCEEDED;
    }
}

package com.guardbench.testrun.domain;

import java.util.Objects;

public record SnapshotExecutionPair(
        TestCaseSnapshotId snapshotId,
        TestExecutionStatus baselineStatus,
        TestExecutionStatus candidateStatus
) {

    public SnapshotExecutionPair {
        Objects.requireNonNull(snapshotId, "snapshot ID must not be null");
    }

    public boolean isProcessed() {
        return baselineStatus != null
                && candidateStatus != null
                && baselineStatus.isTerminal()
                && candidateStatus.isTerminal();
    }

    public int succeededExecutionCount() {
        return (baselineStatus == TestExecutionStatus.SUCCEEDED ? 1 : 0)
                + (candidateStatus == TestExecutionStatus.SUCCEEDED ? 1 : 0);
    }
}

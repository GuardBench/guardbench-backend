package com.guardbench.testrun.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;

@Embeddable
class TestExecutionEntityId implements Serializable {
    Long snapshotId;
    String targetType;

    protected TestExecutionEntityId() {
    }

    TestExecutionEntityId(Long snapshotId, String targetType) {
        this.snapshotId = snapshotId;
        this.targetType = targetType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TestExecutionEntityId that)) {
            return false;
        }
        return Objects.equals(snapshotId, that.snapshotId)
                && Objects.equals(targetType, that.targetType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotId, targetType);
    }
}

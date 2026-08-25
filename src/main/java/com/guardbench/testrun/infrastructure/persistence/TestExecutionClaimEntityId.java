package com.guardbench.testrun.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class TestExecutionClaimEntityId implements Serializable {
    @Column(name = "snapshot_id")
    Long snapshotId;

    @Column(name = "target_type")
    String targetType;

    protected TestExecutionClaimEntityId() {
    }

    TestExecutionClaimEntityId(Long snapshotId, String targetType) {
        this.snapshotId = snapshotId;
        this.targetType = targetType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TestExecutionClaimEntityId that)) {
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

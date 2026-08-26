package com.guardbench.evaluation.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "change_result")
class ChangeResultEntity {
    @Id long snapshotId;
    String comparabilityStatus;
    String changeType;
    Instant createdAt;

    protected ChangeResultEntity() {
    }

    private ChangeResultEntity(
            long snapshotId,
            String comparabilityStatus,
            String changeType,
            Instant createdAt) {
        this.snapshotId = snapshotId;
        this.comparabilityStatus = comparabilityStatus;
        this.changeType = changeType;
        this.createdAt = createdAt;
    }

    static ChangeResultEntity of(
            long snapshotId,
            String comparabilityStatus,
            String changeType,
            Instant createdAt) {
        return new ChangeResultEntity(snapshotId, comparabilityStatus, changeType, createdAt);
    }
}

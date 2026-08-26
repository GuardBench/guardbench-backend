package com.guardbench.evaluation.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assertion_result")
class AssertionResultEntity {
    @Id long snapshotId;
    String assertionStatus;
    Instant createdAt;

    protected AssertionResultEntity() {
    }

    private AssertionResultEntity(long snapshotId, String assertionStatus, Instant createdAt) {
        this.snapshotId = snapshotId;
        this.assertionStatus = assertionStatus;
        this.createdAt = createdAt;
    }

    static AssertionResultEntity of(long snapshotId, String assertionStatus, Instant createdAt) {
        return new AssertionResultEntity(snapshotId, assertionStatus, createdAt);
    }
}

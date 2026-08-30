package com.guardbench.testrun.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_execution")
class TestExecutionEntity {
    @Id Long snapshotId;
    String resultStatus;
    String actualAction;
    String errorCode;
    String errorMessage;
    Instant startedAt;
    Instant completedAt;

    protected TestExecutionEntity() {
    }

    private TestExecutionEntity(
            Long snapshotId,
            String resultStatus,
            String actualAction,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {
        this.snapshotId = snapshotId;
        this.resultStatus = resultStatus;
        this.actualAction = actualAction;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    static TestExecutionEntity of(
            Long snapshotId,
            String resultStatus,
            String actualAction,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {
        return new TestExecutionEntity(snapshotId, resultStatus, actualAction, errorCode, errorMessage, startedAt, completedAt);
    }
}

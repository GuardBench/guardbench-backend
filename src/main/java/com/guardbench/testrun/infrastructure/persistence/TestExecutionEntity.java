package com.guardbench.testrun.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_execution")
class TestExecutionEntity {
    @EmbeddedId TestExecutionEntityId id;
    String resultStatus;
    String actualAction;
    String errorCode;
    String errorMessage;
    Instant startedAt;
    Instant completedAt;

    protected TestExecutionEntity() {
    }

    private TestExecutionEntity(
            TestExecutionEntityId id,
            String resultStatus,
            String actualAction,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {
        this.id = id;
        this.resultStatus = resultStatus;
        this.actualAction = actualAction;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    static TestExecutionEntity of(
            TestExecutionEntityId id,
            String resultStatus,
            String actualAction,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {
        return new TestExecutionEntity(id, resultStatus, actualAction, errorCode, errorMessage, startedAt, completedAt);
    }
}

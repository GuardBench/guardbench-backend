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
    String applicationResponse;
    String evaluatorVerdict;
    String errorStage;
    String errorCode;
    String errorMessage;
    Instant startedAt;
    Instant completedAt;

    protected TestExecutionEntity() {
    }

    private TestExecutionEntity(
            Long snapshotId,
            String resultStatus,
            String applicationResponse,
            String evaluatorVerdict,
            String errorStage,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {
        this.snapshotId = snapshotId;
        this.resultStatus = resultStatus;
        this.applicationResponse = applicationResponse;
        this.evaluatorVerdict = evaluatorVerdict;
        this.errorStage = errorStage;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    static TestExecutionEntity of(
            Long snapshotId,
            String resultStatus,
            String applicationResponse,
            String evaluatorVerdict,
            String errorStage,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {
        return new TestExecutionEntity(snapshotId, resultStatus, applicationResponse,
                evaluatorVerdict, errorStage, errorCode, errorMessage, startedAt, completedAt);
    }
}

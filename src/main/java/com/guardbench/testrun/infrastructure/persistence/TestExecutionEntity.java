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
}

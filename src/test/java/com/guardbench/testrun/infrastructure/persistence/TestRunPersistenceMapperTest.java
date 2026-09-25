package com.guardbench.testrun.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestRunPersistenceMapperTest {

    private static final Instant EXECUTED_AT = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    @DisplayName("FAILED row without an error code fails with a persistence diagnostic")
    void rejectsFailedExecutionWithoutErrorCode() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> TestRunPersistenceMapper.toDomain(execution("FAILED", null)));

        assertTrue(exception.getMessage().contains("FAILED execution requires an error code"));
        assertTrue(exception.getMessage().contains("snapshot_id=1000"));
    }

    @Test
    @DisplayName("unknown persisted error code fails with a persistence diagnostic")
    void rejectsUnknownErrorCode() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> TestRunPersistenceMapper.toDomain(execution("FAILED", "UNKNOWN_ERROR")));

        assertTrue(exception.getMessage().contains("unknown error code 'UNKNOWN_ERROR'"));
        assertTrue(exception.getMessage().contains("snapshot_id=1000"));
    }

    private static TestExecutionEntity execution(String status, String errorCode) {
        return TestExecutionEntity.of(
                1000L,
                status,
                null,
                null,
                "APPLICATION_TARGET",
                errorCode,
                "안전한 오류",
                EXECUTED_AT,
                EXECUTED_AT);
    }
}

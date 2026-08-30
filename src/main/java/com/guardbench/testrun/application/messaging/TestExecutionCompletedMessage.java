package com.guardbench.testrun.application.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 단일 Target 실행 완료를 알리는 TestExecutionCompleted v2 메시지다. */
public record TestExecutionCompletedMessage(
        UUID eventId,
        long testRunId,
        long snapshotId,
        Instant occurredAt
) implements TestRunMessage {

    public TestExecutionCompletedMessage {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (testRunId <= 0 || snapshotId <= 0) {
            throw new IllegalArgumentException("testRunId and snapshotId must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public String eventType() {
        return "TestExecutionCompleted";
    }
}

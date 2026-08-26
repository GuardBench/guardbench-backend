package com.guardbench.testrun.application.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** ADR 0005의 TestExecutionCompleted v1 메시지다. */
public record TestExecutionCompletedMessage(
        UUID eventId,
        long testRunId,
        long snapshotId,
        TargetTypeCode targetType,
        Instant occurredAt
) implements TestRunMessage {

    public TestExecutionCompletedMessage {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (testRunId <= 0 || snapshotId <= 0) {
            throw new IllegalArgumentException("testRunId and snapshotId must be positive");
        }
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public String eventType() {
        return "TestExecutionCompleted";
    }
}

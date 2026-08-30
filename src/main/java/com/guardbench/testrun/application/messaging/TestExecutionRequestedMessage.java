package com.guardbench.testrun.application.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 단일 Target 실행을 요청하는 TestExecutionRequested v2 메시지다. */
public record TestExecutionRequestedMessage(
        UUID eventId,
        long testRunId,
        long snapshotId,
        Instant occurredAt
) implements TestRunMessage {

    public TestExecutionRequestedMessage {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (testRunId <= 0 || snapshotId <= 0) {
            throw new IllegalArgumentException("testRunId and snapshotId must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public String eventType() {
        return "TestExecutionRequested";
    }
}

package com.guardbench.testrun.application.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** ADR 0005의 TestRunRequested v1 메시지다. */
public record TestRunRequestedMessage(UUID eventId, long testRunId, Instant occurredAt) implements TestRunMessage {

    public TestRunRequestedMessage {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (testRunId <= 0) {
            throw new IllegalArgumentException("testRunId must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    @Override
    public String eventType() {
        return "TestRunRequested";
    }
}

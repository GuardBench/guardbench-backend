package com.guardbench.testrun.application.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR 0005 v1 SQS 메시지의 framework-independent 값 계약이다.
 */
public sealed interface TestRunMessage permits TestRunRequestedMessage, TestExecutionRequestedMessage,
        TestExecutionCompletedMessage {

    UUID eventId();

    long testRunId();

    Instant occurredAt();

    String eventType();
}

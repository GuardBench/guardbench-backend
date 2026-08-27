package com.guardbench.testrun.application.messaging;

/** ADR 0005가 정한 event type별 SQS queue 이름이다. */
public enum TestRunQueue {
    RESOLVE("gb-run-resolve"),
    WORK_ITEMS("gb-workitems"),
    FINALIZE("gb-run-finalize");

    private final String queueName;

    TestRunQueue(String queueName) {
        this.queueName = queueName;
    }

    public String queueName() {
        return queueName;
    }

    public String deadLetterQueueName() {
        return queueName + "-dlq";
    }

    public static TestRunQueue forEventType(String eventType) {
        return switch (eventType) {
            case "TestRunRequested" -> RESOLVE;
            case "TestExecutionRequested" -> WORK_ITEMS;
            case "TestExecutionCompleted" -> FINALIZE;
            default -> throw new IllegalArgumentException("unsupported eventType: " + eventType);
        };
    }
}

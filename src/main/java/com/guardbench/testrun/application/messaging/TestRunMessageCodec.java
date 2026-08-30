package com.guardbench.testrun.application.messaging;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 단일 Target v2 메시지의 JSON 경계다. 알 수 없는 optional field는 읽을 때 무시한다.
 */
public final class TestRunMessageCodec {

    public static final int SCHEMA_VERSION = 2;

    private final ObjectMapper objectMapper;

    public TestRunMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(TestRunMessage message) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", message.eventId().toString());
        root.put("eventType", message.eventType());
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("testRunId", message.testRunId());
        root.put("occurredAt", message.occurredAt().toString());
        if (message instanceof TestExecutionRequestedMessage executionRequested) {
            addExecutionFields(root, executionRequested.snapshotId());
        } else if (message instanceof TestExecutionCompletedMessage executionCompleted) {
            addExecutionFields(root, executionCompleted.snapshotId());
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException exception) {
            throw new IllegalStateException("cannot encode TestRun v2 message", exception);
        }
    }

    public TestRunMessage decode(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw invalid("message must be a JSON object");
            }
            UUID eventId = UUID.fromString(requireText(root, "eventId"));
            String eventType = requireText(root, "eventType");
            if (!root.has("schemaVersion") || !root.get("schemaVersion").canConvertToInt()
                    || root.get("schemaVersion").intValue() != SCHEMA_VERSION) {
                throw invalid("unsupported schemaVersion");
            }
            long testRunId = requirePositiveLong(root, "testRunId");
            Instant occurredAt = Instant.parse(requireText(root, "occurredAt"));
            return switch (eventType) {
                case "TestRunRequested" -> new TestRunRequestedMessage(eventId, testRunId, occurredAt);
                case "TestExecutionRequested" -> new TestExecutionRequestedMessage(
                        eventId, testRunId, requirePositiveLong(root, "snapshotId"), occurredAt);
                case "TestExecutionCompleted" -> new TestExecutionCompletedMessage(
                        eventId, testRunId, requirePositiveLong(root, "snapshotId"), occurredAt);
                default -> throw invalid("unsupported eventType: " + eventType);
            };
        } catch (JacksonException | IllegalArgumentException exception) {
            if (exception instanceof InvalidTestRunMessageException invalid) {
                throw invalid;
            }
            throw invalid("invalid v2 message", exception);
        }
    }

    private static void addExecutionFields(ObjectNode root, long snapshotId) {
        root.put("snapshotId", snapshotId);
    }

    private static String requireText(JsonNode root, String field) {
        if (!root.has(field) || !root.get(field).isTextual() || root.get(field).textValue().isBlank()) {
            throw invalid("missing or invalid " + field);
        }
        return root.get(field).textValue();
    }

    private static long requirePositiveLong(JsonNode root, String field) {
        if (!root.has(field) || !root.get(field).canConvertToLong() || root.get(field).longValue() <= 0) {
            throw invalid("missing or invalid " + field);
        }
        return root.get(field).longValue();
    }

    private static InvalidTestRunMessageException invalid(String message) {
        return new InvalidTestRunMessageException(message);
    }

    private static InvalidTestRunMessageException invalid(String message, Exception cause) {
        return new InvalidTestRunMessageException(message, cause);
    }
}

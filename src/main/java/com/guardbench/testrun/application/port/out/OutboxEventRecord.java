package com.guardbench.testrun.application.port.out;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Outbox 이벤트의 불변 스냅샷 값이다.
 * Application이 소유하며 SQS/publisher 의존 없이 PENDING 이벤트를 저장한다.
 */
public record OutboxEventRecord(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String payload,
        String deduplicationKey,
        String status,
        Instant createdAt,
        Instant publishedAt
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    public OutboxEventRecord {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("schemaVersion must be 1");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must not be blank");
        }
        validatePayload(eventId, eventType, schemaVersion, payload);
        if (deduplicationKey == null || deduplicationKey.isBlank()) {
            throw new IllegalArgumentException("deduplicationKey must not be blank");
        }
        if (!STATUS_PENDING.equals(status) && !STATUS_PUBLISHED.equals(status)) {
            throw new IllegalArgumentException("status must be PENDING or PUBLISHED");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (STATUS_PENDING.equals(status) && publishedAt != null) {
            throw new IllegalArgumentException("PENDING event must not have publishedAt");
        }
        if (STATUS_PUBLISHED.equals(status) && publishedAt == null) {
            throw new IllegalArgumentException("PUBLISHED event must have publishedAt");
        }
    }

    private static void validatePayload(UUID eventId, String eventType, int schemaVersion, String payload) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("payload must be a JSON object");
            }
            requireText(root, "eventId");
            requireText(root, "eventType");
            requireText(root, "occurredAt");
            if (!eventId.toString().equals(root.get("eventId").textValue())
                    || !eventType.equals(root.get("eventType").textValue())
                    || !root.has("schemaVersion") || root.get("schemaVersion").asInt() != schemaVersion) {
                throw new IllegalArgumentException("payload header must match Outbox event fields");
            }
            Instant.parse(root.get("occurredAt").textValue());
            requirePositiveLong(root, "testRunId");
            switch (eventType) {
                case "TestRunRequested" -> { }
                case "TestExecutionRequested", "TestExecutionCompleted" -> {
                    requirePositiveLong(root, "snapshotId");
                    String targetType = requireText(root, "targetType");
                    if (!"BASELINE".equals(targetType) && !"CANDIDATE".equals(targetType)) {
                        throw new IllegalArgumentException("payload targetType must be BASELINE or CANDIDATE");
                    }
                }
                default -> throw new IllegalArgumentException("unsupported eventType: " + eventType);
            }
        } catch (JacksonException | DateTimeException exception) {
            throw new IllegalArgumentException("payload must be a valid v1 event JSON", exception);
        }
    }

    private static String requireText(JsonNode root, String field) {
        if (!root.has(field) || !root.get(field).isTextual() || root.get(field).textValue().isBlank()) {
            throw new IllegalArgumentException("payload " + field + " must be a nonblank string");
        }
        return root.get(field).textValue();
    }

    private static void requirePositiveLong(JsonNode root, String field) {
        if (!root.has(field) || !root.get(field).canConvertToLong() || root.get(field).longValue() <= 0) {
            throw new IllegalArgumentException("payload " + field + " must be a positive integer");
        }
    }

    /**
     * PENDING 이벤트를 생성하는 팩토리 메서드다.
     */
    public static OutboxEventRecord pending(
            UUID eventId,
            String eventType,
            String payload,
            String deduplicationKey,
            Instant createdAt
    ) {
        return new OutboxEventRecord(eventId, eventType, 1, payload, deduplicationKey, STATUS_PENDING, createdAt, null);
    }
}

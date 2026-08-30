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

    /** Outbox 발행 운영 로그에 사용하는, 메시지 payload의 비민감 식별자다. */
    public record ObservabilityContext(long testRunId, Long snapshotId) {}

    public OutboxEventRecord {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (schemaVersion != 1 && schemaVersion != 2) {
            throw new IllegalArgumentException("schemaVersion must be 1 or 2");
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
                    if (schemaVersion == 1) {
                        String targetType = requireText(root, "targetType");
                        if (!"BASELINE".equals(targetType) && !"CANDIDATE".equals(targetType)) {
                            throw new IllegalArgumentException("payload targetType must be BASELINE or CANDIDATE");
                        }
                    }
                }
                default -> throw new IllegalArgumentException("unsupported eventType: " + eventType);
            }
        } catch (JacksonException | DateTimeException exception) {
            throw new IllegalArgumentException("payload must be valid event JSON", exception);
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
        return new OutboxEventRecord(eventId, eventType, 2, payload, deduplicationKey, STATUS_PENDING, createdAt, null);
    }

    /**
     * 이미 검증된 v1 payload에서 운영 추적에 필요한 식별자만 읽는다.
     * 입력, 실행 결과, Provider 응답은 반환하지 않는다.
     */
    public ObservabilityContext observabilityContext() {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            Long snapshotId = root.has("snapshotId") ? root.get("snapshotId").longValue() : null;
            return new ObservabilityContext(root.get("testRunId").longValue(), snapshotId);
        } catch (JacksonException exception) {
            throw new IllegalStateException("validated Outbox payload could not be read", exception);
        }
    }
}

package com.guardbench.testrun.application.port.out;

import java.time.Instant;
import java.util.UUID;

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

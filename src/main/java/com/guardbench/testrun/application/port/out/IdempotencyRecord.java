package com.guardbench.testrun.application.port.out;

import java.time.Instant;

/**
 * Idempotency 레코드의 불변 스냅샷 값이다.
 * Application이 소유하며 Infrastructure 경계의 구현 세부를 노출하지 않는다.
 */
public record IdempotencyRecord(
        String idempotencyKey,
        String requestFingerprint,
        long testRunId,
        Instant createdAt,
        Instant expiresAt
) {

    public IdempotencyRecord {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (requestFingerprint == null || requestFingerprint.length() != 64) {
            throw new IllegalArgumentException("requestFingerprint must be a 64-char SHA-256 hex");
        }
        if (testRunId <= 0) {
            throw new IllegalArgumentException("testRunId must be positive");
        }
        if (createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException("timestamps must not be null");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }
}

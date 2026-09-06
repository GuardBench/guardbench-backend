package com.guardbench.testdefinition.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TestCaseBulkIdempotencyPort {

    boolean tryClaim(
            String idempotencyKey,
            String requestFingerprint,
            long testSuiteId,
            Instant createdAt,
            Instant expiresAt);

    Optional<TestCaseBulkIdempotencyRecord> findActiveByKey(String idempotencyKey);

    void complete(
            String idempotencyKey,
            String requestFingerprint,
            long testSuiteId,
            List<Long> createdTestCaseIds,
            long totalTestCaseCount);
}

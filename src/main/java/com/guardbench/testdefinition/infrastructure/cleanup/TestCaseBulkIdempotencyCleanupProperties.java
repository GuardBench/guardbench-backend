package com.guardbench.testdefinition.infrastructure.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "guardbench.test-case-bulk-idempotency.cleanup")
record TestCaseBulkIdempotencyCleanupProperties(
        Integer batchSize,
        Integer maxBatchesPerRun,
        Long delayMs,
        Long initialDelayMs
) {

    static final int DEFAULT_BATCH_SIZE = 500;
    static final int DEFAULT_MAX_BATCHES_PER_RUN = 10;
    static final long DEFAULT_DELAY_MS = 900_000L;
    static final long DEFAULT_INITIAL_DELAY_MS = 60_000L;

    TestCaseBulkIdempotencyCleanupProperties {
        if (batchSize == null) {
            batchSize = DEFAULT_BATCH_SIZE;
        } else if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        if (maxBatchesPerRun == null) {
            maxBatchesPerRun = DEFAULT_MAX_BATCHES_PER_RUN;
        } else if (maxBatchesPerRun < 1 || maxBatchesPerRun > 100) {
            throw new IllegalArgumentException("maxBatchesPerRun must be between 1 and 100");
        }
        if (delayMs == null) {
            delayMs = DEFAULT_DELAY_MS;
        } else if (delayMs < 1000) {
            throw new IllegalArgumentException("delayMs must be at least 1000");
        }
        if (initialDelayMs == null) {
            initialDelayMs = DEFAULT_INITIAL_DELAY_MS;
        } else if (initialDelayMs < 0) {
            throw new IllegalArgumentException("initialDelayMs must not be negative");
        }
    }
}

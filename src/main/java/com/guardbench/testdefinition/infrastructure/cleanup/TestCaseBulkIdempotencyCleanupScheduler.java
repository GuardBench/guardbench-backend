package com.guardbench.testdefinition.infrastructure.cleanup;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.guardbench.testdefinition.application.TestCaseBulkIdempotencyCleanupService;

@Component
@ConditionalOnProperty(
        name = "guardbench.test-case-bulk-idempotency.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
class TestCaseBulkIdempotencyCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TestCaseBulkIdempotencyCleanupScheduler.class);

    private final TestCaseBulkIdempotencyCleanupService cleanupService;
    private final int batchSize;
    private final int maxBatchesPerRun;

    TestCaseBulkIdempotencyCleanupScheduler(
            TestCaseBulkIdempotencyCleanupService cleanupService,
            TestCaseBulkIdempotencyCleanupProperties properties) {
        this.cleanupService = cleanupService;
        this.batchSize = properties.batchSize();
        this.maxBatchesPerRun = properties.maxBatchesPerRun();
    }

    @Scheduled(
            fixedDelayString = "${guardbench.test-case-bulk-idempotency.cleanup.delay-ms:900000}",
            initialDelayString = "${guardbench.test-case-bulk-idempotency.cleanup.initial-delay-ms:60000}")
    void deleteExpiredRecords() {
        long startedAt = System.nanoTime();
        int deletedCount = 0;
        int batchCount = 0;
        boolean limitReached = false;
        try {
            int deletedInBatch;
            do {
                deletedInBatch = cleanupService.deleteExpiredBatch(batchSize);
                deletedCount += deletedInBatch;
                batchCount++;
            } while (deletedInBatch == batchSize && batchCount < maxBatchesPerRun);
            limitReached = deletedInBatch == batchSize && batchCount == maxBatchesPerRun;

            log.info(
                    "TestCase bulk idempotency cleanup completed. deletedCount={} batchCount={} durationMs={} batchSize={} maxBatchesPerRun={} limitReached={}",
                    deletedCount,
                    batchCount,
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                    batchSize,
                    maxBatchesPerRun,
                    limitReached);
        } catch (Exception exception) {
            log.error(
                    "TestCase bulk idempotency cleanup failed. deletedCount={} batchCount={} durationMs={} batchSize={} maxBatchesPerRun={}",
                    deletedCount,
                    batchCount,
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                    batchSize,
                    maxBatchesPerRun,
                    exception);
        }
    }
}

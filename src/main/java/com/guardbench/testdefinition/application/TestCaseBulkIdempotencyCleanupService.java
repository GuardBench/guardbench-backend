package com.guardbench.testdefinition.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testdefinition.application.port.out.TestCaseBulkIdempotencyCleanupPort;

@Service
public class TestCaseBulkIdempotencyCleanupService {

    private final TestCaseBulkIdempotencyCleanupPort cleanupPort;

    public TestCaseBulkIdempotencyCleanupService(TestCaseBulkIdempotencyCleanupPort cleanupPort) {
        this.cleanupPort = cleanupPort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpiredBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return cleanupPort.deleteExpiredBatch(batchSize);
    }
}

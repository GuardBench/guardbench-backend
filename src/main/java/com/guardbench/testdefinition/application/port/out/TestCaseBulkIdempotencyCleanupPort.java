package com.guardbench.testdefinition.application.port.out;

public interface TestCaseBulkIdempotencyCleanupPort {

    int deleteExpiredBatch(int batchSize);
}

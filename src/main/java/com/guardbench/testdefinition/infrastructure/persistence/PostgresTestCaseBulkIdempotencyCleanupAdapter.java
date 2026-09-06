package com.guardbench.testdefinition.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testdefinition.application.port.out.TestCaseBulkIdempotencyCleanupPort;

@Repository
class PostgresTestCaseBulkIdempotencyCleanupAdapter implements TestCaseBulkIdempotencyCleanupPort {

    private final JdbcTemplate jdbcTemplate;

    PostgresTestCaseBulkIdempotencyCleanupAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int deleteExpiredBatch(int batchSize) {
        return jdbcTemplate.update("""
                WITH expired AS (
                    SELECT idempotency_key
                    FROM test_case_bulk_idempotency
                    WHERE expires_at <= clock_timestamp()
                    ORDER BY expires_at, idempotency_key
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                DELETE FROM test_case_bulk_idempotency target
                USING expired
                WHERE target.idempotency_key = expired.idempotency_key
                """, batchSize);
    }
}

package com.guardbench.testdefinition.infrastructure.persistence;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testdefinition.application.port.out.TestCaseBulkIdempotencyPort;
import com.guardbench.testdefinition.application.port.out.TestCaseBulkIdempotencyRecord;

@Repository
class PostgresTestCaseBulkIdempotencyAdapter implements TestCaseBulkIdempotencyPort {

    private final JdbcTemplate jdbcTemplate;

    PostgresTestCaseBulkIdempotencyAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryClaim(
            String idempotencyKey,
            String requestFingerprint,
            long testSuiteId,
            Instant createdAt,
            Instant expiresAt) {
        int changed = jdbcTemplate.update(
                """
                INSERT INTO test_case_bulk_idempotency (
                    idempotency_key, request_fingerprint, test_suite_id,
                    created_test_case_ids, total_test_case_count, created_at, expires_at)
                VALUES (?, ?, ?, NULL, NULL, ?, ?)
                ON CONFLICT (idempotency_key) DO UPDATE
                SET request_fingerprint = EXCLUDED.request_fingerprint,
                    test_suite_id = EXCLUDED.test_suite_id,
                    created_test_case_ids = NULL,
                    total_test_case_count = NULL,
                    created_at = EXCLUDED.created_at,
                    expires_at = EXCLUDED.expires_at
                WHERE test_case_bulk_idempotency.expires_at <= clock_timestamp()
                """,
                idempotencyKey,
                requestFingerprint,
                testSuiteId,
                Timestamp.from(createdAt),
                Timestamp.from(expiresAt));
        return changed == 1;
    }

    @Override
    public Optional<TestCaseBulkIdempotencyRecord> findActiveByKey(String idempotencyKey) {
        List<TestCaseBulkIdempotencyRecord> results = jdbcTemplate.query(
                """
                SELECT request_fingerprint, test_suite_id, created_test_case_ids,
                       total_test_case_count
                FROM test_case_bulk_idempotency
                WHERE idempotency_key = ?
                  AND expires_at > clock_timestamp()
                  AND created_test_case_ids IS NOT NULL
                """,
                (resultSet, rowNumber) -> mapRow(resultSet),
                idempotencyKey);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void complete(
            String idempotencyKey,
            String requestFingerprint,
            long testSuiteId,
            List<Long> createdTestCaseIds,
            long totalTestCaseCount) {
        int changed = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    UPDATE test_case_bulk_idempotency
                    SET created_test_case_ids = ?, total_test_case_count = ?
                    WHERE idempotency_key = ?
                      AND request_fingerprint = ?
                      AND test_suite_id = ?
                      AND created_test_case_ids IS NULL
                    """);
            statement.setArray(1, connection.createArrayOf("bigint", createdTestCaseIds.toArray()));
            statement.setLong(2, totalTestCaseCount);
            statement.setString(3, idempotencyKey);
            statement.setString(4, requestFingerprint);
            statement.setLong(5, testSuiteId);
            return statement;
        });
        if (changed != 1) {
            throw new IllegalStateException("TestCase bulk idempotency claim could not be completed");
        }
    }

    private static TestCaseBulkIdempotencyRecord mapRow(ResultSet resultSet) throws SQLException {
        Array sqlArray = resultSet.getArray("created_test_case_ids");
        Long[] ids = (Long[]) sqlArray.getArray();
        return new TestCaseBulkIdempotencyRecord(
                resultSet.getString("request_fingerprint").trim(),
                resultSet.getLong("test_suite_id"),
                Arrays.asList(ids),
                resultSet.getLong("total_test_case_count"));
    }
}

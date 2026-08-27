package com.guardbench.testrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.IdempotencyPort;
import com.guardbench.testrun.application.port.out.IdempotencyRecord;

@Repository
class PostgresIdempotencyAdapter implements IdempotencyPort {

    private final JdbcTemplate jdbcTemplate;

    PostgresIdempotencyAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<IdempotencyRecord> findActiveByKey(String idempotencyKey) {
        var results = jdbcTemplate.query(
                """
                SELECT idempotency_key, request_fingerprint, test_run_id, created_at, expires_at
                FROM test_run_idempotency
                WHERE idempotency_key = ? AND expires_at > clock_timestamp()
                """,
                (rs, rowNum) -> mapRow(rs),
                idempotencyKey
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void save(IdempotencyRecord record) {
        jdbcTemplate.update(
                """
                INSERT INTO test_run_idempotency (idempotency_key, request_fingerprint, test_run_id, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO UPDATE
                SET request_fingerprint = EXCLUDED.request_fingerprint,
                    test_run_id = EXCLUDED.test_run_id,
                    created_at = EXCLUDED.created_at,
                    expires_at = EXCLUDED.expires_at
                WHERE test_run_idempotency.expires_at <= clock_timestamp()
                """,
                record.idempotencyKey(),
                record.requestFingerprint(),
                record.testRunId(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.expiresAt())
        );
    }

    private static IdempotencyRecord mapRow(ResultSet rs) throws SQLException {
        return new IdempotencyRecord(
                rs.getString("idempotency_key"),
                rs.getString("request_fingerprint").trim(),
                rs.getLong("test_run_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()
        );
    }
}

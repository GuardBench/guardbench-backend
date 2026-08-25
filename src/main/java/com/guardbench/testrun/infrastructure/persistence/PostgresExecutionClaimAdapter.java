package com.guardbench.testrun.infrastructure.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;

@Repository
class PostgresExecutionClaimAdapter implements ExecutionClaimPort {

    private static final int LEASE_SECONDS = 45;

    private final JdbcTemplate jdbcTemplate;

    PostgresExecutionClaimAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ClaimResult tryAcquire(long snapshotId, String targetType) {
        UUID newToken = UUID.randomUUID();
        var rows = jdbcTemplate.query(
                """
                INSERT INTO test_execution_claim (snapshot_id, target_type, claim_token, lease_until, attempt_count, claimed_at, updated_at)
                VALUES (?, ?, ?::uuid, clock_timestamp() + INTERVAL '%d seconds', 1, clock_timestamp(), clock_timestamp())
                ON CONFLICT (snapshot_id, target_type) DO UPDATE
                SET claim_token = EXCLUDED.claim_token,
                    lease_until = clock_timestamp() + INTERVAL '%d seconds',
                    attempt_count = test_execution_claim.attempt_count + 1,
                    updated_at = clock_timestamp()
                WHERE test_execution_claim.lease_until <= clock_timestamp()
                RETURNING claim_token, attempt_count
                """.formatted(LEASE_SECONDS, LEASE_SECONDS),
                (rs, rowNum) -> new ClaimResult.Acquired(
                        UUID.fromString(rs.getString("claim_token")),
                        rs.getInt("attempt_count")
                ),
                snapshotId,
                targetType,
                newToken.toString()
        );
        if (rows.isEmpty()) {
            return new ClaimResult.AlreadyHeld();
        }
        return rows.getFirst();
    }

    @Override
    public boolean isHeldBy(long snapshotId, String targetType, UUID claimToken) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM test_execution_claim
                WHERE snapshot_id = ? AND target_type = ? AND claim_token = ?::uuid AND lease_until > clock_timestamp()
                """,
                Integer.class,
                snapshotId,
                targetType,
                claimToken.toString()
        );
        return count != null && count > 0;
    }
}

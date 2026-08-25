package com.guardbench.testrun.infrastructure.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ResolutionClaimPort;

@Repository
class PostgresResolutionClaimAdapter implements ResolutionClaimPort {

    private static final int LEASE_SECONDS = 45;

    private final JdbcTemplate jdbcTemplate;

    PostgresResolutionClaimAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ClaimResult tryAcquire(long testRunId) {
        UUID newToken = UUID.randomUUID();
        var rows = jdbcTemplate.query(
                """
                INSERT INTO test_run_resolution_claim (test_run_id, claim_token, lease_until, attempt_count, claimed_at, updated_at)
                VALUES (?, ?::uuid, clock_timestamp() + INTERVAL '%d seconds', 1, clock_timestamp(), clock_timestamp())
                ON CONFLICT (test_run_id) DO UPDATE
                SET claim_token = EXCLUDED.claim_token,
                    lease_until = clock_timestamp() + INTERVAL '%d seconds',
                    attempt_count = test_run_resolution_claim.attempt_count + 1,
                    updated_at = clock_timestamp()
                WHERE test_run_resolution_claim.lease_until <= clock_timestamp()
                RETURNING claim_token, attempt_count
                """.formatted(LEASE_SECONDS, LEASE_SECONDS),
                (rs, rowNum) -> new ClaimResult.Acquired(
                        UUID.fromString(rs.getString("claim_token")),
                        rs.getInt("attempt_count")
                ),
                testRunId,
                newToken.toString()
        );
        if (rows.isEmpty()) {
            return new ClaimResult.AlreadyHeld();
        }
        return rows.getFirst();
    }

    @Override
    public boolean isHeldBy(long testRunId, UUID claimToken) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM test_run_resolution_claim
                WHERE test_run_id = ? AND claim_token = ?::uuid AND lease_until > clock_timestamp()
                """,
                Integer.class,
                testRunId,
                claimToken.toString()
        );
        return count != null && count > 0;
    }
}

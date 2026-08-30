package com.guardbench.testrun.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;

@Repository
class PostgresExecutionClaimAdapter implements ExecutionClaimPort {

    private static final int LEASE_SECONDS = 45;

    private final EntityManager entityManager;

    PostgresExecutionClaimAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public ClaimResult tryAcquire(long snapshotId) {
        UUID newToken = UUID.randomUUID();
        List<?> rows = entityManager.createNativeQuery(
                        """
                        INSERT INTO test_execution_claim (snapshot_id, claim_token, lease_until, attempt_count, claimed_at, updated_at)
                        VALUES (:snapshotId, CAST(:claimToken AS uuid), clock_timestamp() + INTERVAL '%d seconds', 1, clock_timestamp(), clock_timestamp())
                        ON CONFLICT (snapshot_id) DO UPDATE
                        SET claim_token = EXCLUDED.claim_token,
                            lease_until = clock_timestamp() + INTERVAL '%d seconds',
                            attempt_count = test_execution_claim.attempt_count + 1,
                            updated_at = clock_timestamp()
                        WHERE test_execution_claim.lease_until <= clock_timestamp()
                        RETURNING claim_token, attempt_count
                        """.formatted(LEASE_SECONDS, LEASE_SECONDS))
                .setParameter("snapshotId", snapshotId)
                .setParameter("claimToken", newToken.toString())
                .getResultList();
        if (rows.isEmpty()) {
            return new ClaimResult.AlreadyHeld();
        }
        return acquired(rows.getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isHeldBy(long snapshotId, UUID claimToken) {
        Object held = entityManager.createNativeQuery(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM test_execution_claim
                            WHERE snapshot_id = :snapshotId
                              AND claim_token = CAST(:claimToken AS uuid)
                              AND lease_until > clock_timestamp()
                        )
                        """)
                .setParameter("snapshotId", snapshotId)
                .setParameter("claimToken", claimToken.toString())
                .getSingleResult();
        return Boolean.TRUE.equals(held);
    }

    private static ClaimResult.Acquired acquired(Object row) {
        Object[] values = (Object[]) row;
        return new ClaimResult.Acquired(
                UUID.fromString(values[0].toString()),
                ((Number) values[1]).intValue()
        );
    }
}

package com.guardbench.testrun.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_execution_claim")
class TestExecutionClaimEntity {
    @EmbeddedId
    TestExecutionClaimEntityId id;

    @Column(name = "claim_token")
    UUID claimToken;

    @Column(name = "lease_until")
    Instant leaseUntil;

    @Column(name = "attempt_count")
    int attemptCount;

    @Column(name = "claimed_at")
    Instant claimedAt;

    @Column(name = "updated_at")
    Instant updatedAt;

    protected TestExecutionClaimEntity() {
    }
}

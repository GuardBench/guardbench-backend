package com.guardbench.testrun.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_run_resolution_claim")
class TestRunResolutionClaimEntity {
    @Id
    @Column(name = "test_run_id")
    Long testRunId;

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

    protected TestRunResolutionClaimEntity() {
    }
}

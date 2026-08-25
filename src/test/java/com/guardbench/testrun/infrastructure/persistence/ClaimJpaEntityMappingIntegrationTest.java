package com.guardbench.testrun.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;
import com.guardbench.testrun.application.port.out.ResolutionClaimPort;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class ClaimJpaEntityMappingIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-08-25T00:00:00Z");

    @Autowired
    private ResolutionClaimPort resolutionClaimPort;

    @Autowired
    private ExecutionClaimPort executionClaimPort;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp(@Autowired JdbcTemplate jdbcTemplate) {
        TestRunPersistenceFixture fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        fixture.insertTestSuite(800L, BASE);
        fixture.insertQueuedTestRun(801L, 800L, 1, BASE);
        fixture.insertTestCase(802L, 800L, BASE);
        fixture.insertSnapshot(803L, 801L, 802L, BASE);
    }

    @Test
    @DisplayName("resolution claim 획득 결과를 JPA Entity의 scalar column으로 조회한다")
    void readsResolutionClaimThroughJpaEntity() {
        ClaimResult.Acquired acquired = assertInstanceOf(
                ClaimResult.Acquired.class,
                resolutionClaimPort.tryAcquire(801L));

        entityManager.clear();
        TestRunResolutionClaimEntity claim = entityManager.find(TestRunResolutionClaimEntity.class, 801L);

        assertNotNull(claim);
        assertEquals(acquired.claimToken(), claim.claimToken);
        assertEquals(acquired.attemptCount(), claim.attemptCount);
        assertNotNull(claim.claimedAt);
        assertNotNull(claim.updatedAt);
        assertTrue(claim.leaseUntil.isAfter(claim.claimedAt));
    }

    @Test
    @DisplayName("execution claim 획득 결과를 JPA Entity의 복합 식별자와 scalar column으로 조회한다")
    void readsExecutionClaimThroughJpaEntity() {
        ClaimResult.Acquired acquired = assertInstanceOf(
                ClaimResult.Acquired.class,
                executionClaimPort.tryAcquire(803L, "BASELINE"));

        entityManager.clear();
        TestExecutionClaimEntity claim = entityManager.find(
                TestExecutionClaimEntity.class,
                new TestExecutionClaimEntityId(803L, "BASELINE"));

        assertNotNull(claim);
        assertEquals(803L, claim.id.snapshotId);
        assertEquals("BASELINE", claim.id.targetType);
        assertEquals(acquired.claimToken(), claim.claimToken);
        assertEquals(acquired.attemptCount(), claim.attemptCount);
        assertNotNull(claim.claimedAt);
        assertNotNull(claim.updatedAt);
        assertTrue(claim.leaseUntil.isAfter(claim.claimedAt));
    }
}

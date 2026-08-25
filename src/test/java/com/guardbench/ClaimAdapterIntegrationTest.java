package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;
import com.guardbench.testrun.application.port.out.ResolutionClaimPort;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class ClaimAdapterIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-08-25T00:00:00Z");

    @Autowired
    private ResolutionClaimPort resolutionClaimPort;

    @Autowired
    private ExecutionClaimPort executionClaimPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestRunPersistenceFixture fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        fixture.insertTestSuite(800L, BASE);
        fixture.insertQueuedTestRun(801L, 800L, 1, BASE);
        fixture.insertTestCase(802L, 800L, BASE);
        fixture.insertSnapshot(803L, 801L, 802L, BASE);
    }

    @Nested
    @DisplayName("Resolution Claim")
    class ResolutionClaimTests {

        @Test
        @DisplayName("첫 선점은 Acquired를 반환하고 attemptCount는 1이다")
        void firstAcquire_returnsAcquiredWithAttempt1() {
            ClaimResult result = resolutionClaimPort.tryAcquire(801);
            assertInstanceOf(ClaimResult.Acquired.class, result);
            ClaimResult.Acquired acquired = (ClaimResult.Acquired) result;
            assertEquals(1, acquired.attemptCount());
        }

        @Test
        @DisplayName("유효한 lease가 있으면 두 번째 선점은 AlreadyHeld를 반환한다")
        void secondAcquire_whileLeaseValid_returnsAlreadyHeld() {
            resolutionClaimPort.tryAcquire(801);
            ClaimResult second = resolutionClaimPort.tryAcquire(801);
            assertInstanceOf(ClaimResult.AlreadyHeld.class, second);
        }

        @Test
        @DisplayName("만료된 claim은 새 token으로 재선점되고 attemptCount가 증가한다")
        void expiredClaim_canBeReacquired() {
            resolutionClaimPort.tryAcquire(801);
            // claimed_at과 lease_until을 모두 과거로 변경 (CHECK lease_until >= claimed_at 유지)
            jdbcTemplate.update(
                    """
                    UPDATE test_run_resolution_claim
                    SET claimed_at = clock_timestamp() - INTERVAL '50 seconds',
                        lease_until = clock_timestamp() - INTERVAL '1 second'
                    WHERE test_run_id = 801
                    """);

            ClaimResult result = resolutionClaimPort.tryAcquire(801);
            assertInstanceOf(ClaimResult.Acquired.class, result);
            assertEquals(2, ((ClaimResult.Acquired) result).attemptCount());
        }

        @Test
        @DisplayName("stale token으로 isHeldBy하면 false를 반환한다")
        void staleToken_isHeldBy_returnsFalse() {
            ClaimResult.Acquired first = (ClaimResult.Acquired) resolutionClaimPort.tryAcquire(801);
            // lease 만료 후 새 선점
            jdbcTemplate.update(
                    """
                    UPDATE test_run_resolution_claim
                    SET claimed_at = clock_timestamp() - INTERVAL '50 seconds',
                        lease_until = clock_timestamp() - INTERVAL '1 second'
                    WHERE test_run_id = 801
                    """);
            resolutionClaimPort.tryAcquire(801);

            // 기존 token은 더 이상 유효하지 않음
            assertFalse(resolutionClaimPort.isHeldBy(801, first.claimToken()));
        }

        @Test
        @DisplayName("유효한 token으로 isHeldBy하면 true를 반환한다")
        void validToken_isHeldBy_returnsTrue() {
            ClaimResult.Acquired acquired = (ClaimResult.Acquired) resolutionClaimPort.tryAcquire(801);
            assertTrue(resolutionClaimPort.isHeldBy(801, acquired.claimToken()));
        }
    }

    @Nested
    @DisplayName("Execution Claim")
    class ExecutionClaimTests {

        @Test
        @DisplayName("첫 선점은 Acquired를 반환하고 attemptCount는 1이다")
        void firstAcquire_returnsAcquiredWithAttempt1() {
            ClaimResult result = executionClaimPort.tryAcquire(803, "BASELINE");
            assertInstanceOf(ClaimResult.Acquired.class, result);
            assertEquals(1, ((ClaimResult.Acquired) result).attemptCount());
        }

        @Test
        @DisplayName("유효 lease 동안 중복 선점은 AlreadyHeld를 반환한다")
        void duplicateAcquire_whileValid_returnsAlreadyHeld() {
            executionClaimPort.tryAcquire(803, "BASELINE");
            ClaimResult second = executionClaimPort.tryAcquire(803, "BASELINE");
            assertInstanceOf(ClaimResult.AlreadyHeld.class, second);
        }

        @Test
        @DisplayName("만료된 claim은 새 token으로 재선점되고 attemptCount가 증가한다")
        void expiredClaim_canBeReacquired() {
            executionClaimPort.tryAcquire(803, "CANDIDATE");
            jdbcTemplate.update(
                    """
                    UPDATE test_execution_claim
                    SET claimed_at = clock_timestamp() - INTERVAL '50 seconds',
                        lease_until = clock_timestamp() - INTERVAL '1 second'
                    WHERE snapshot_id = 803 AND target_type = 'CANDIDATE'
                    """);

            ClaimResult result = executionClaimPort.tryAcquire(803, "CANDIDATE");
            assertInstanceOf(ClaimResult.Acquired.class, result);
            assertEquals(2, ((ClaimResult.Acquired) result).attemptCount());
        }

        @Test
        @DisplayName("stale token으로 isHeldBy하면 false를 반환한다")
        void staleToken_isHeldBy_returnsFalse() {
            ClaimResult.Acquired first = (ClaimResult.Acquired) executionClaimPort.tryAcquire(803, "BASELINE");
            jdbcTemplate.update(
                    """
                    UPDATE test_execution_claim
                    SET claimed_at = clock_timestamp() - INTERVAL '50 seconds',
                        lease_until = clock_timestamp() - INTERVAL '1 second'
                    WHERE snapshot_id = 803 AND target_type = 'BASELINE'
                    """);
            executionClaimPort.tryAcquire(803, "BASELINE");

            assertFalse(executionClaimPort.isHeldBy(803, "BASELINE", first.claimToken()));
        }

        @Test
        @DisplayName("유효한 token으로 isHeldBy하면 true를 반환한다")
        void validToken_isHeldBy_returnsTrue() {
            ClaimResult.Acquired acquired = (ClaimResult.Acquired) executionClaimPort.tryAcquire(803, "BASELINE");
            assertTrue(executionClaimPort.isHeldBy(803, "BASELINE", acquired.claimToken()));
        }
    }
}

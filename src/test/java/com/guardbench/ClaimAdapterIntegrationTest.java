package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
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
        jdbcTemplate.execute("TRUNCATE TABLE test_execution_claim, test_run_resolution_claim, outbox_event, test_suite CASCADE");
        jdbcTemplate.update(
                "INSERT INTO test_suite(id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
                800L, "suite", Timestamp.from(BASE), Timestamp.from(BASE));
        jdbcTemplate.update(
                """
                INSERT INTO test_run(id, test_suite_id, status, test_case_count, processed_test_case_count,
                    baseline_guardrail_id, baseline_version, candidate_guardrail_id,
                    candidate_requested_source, created_at, updated_at)
                VALUES (801, 800, 'QUEUED', 1, 0, 'g1', '1', 'g1', 'DRAFT', ?, ?)
                """,
                Timestamp.from(BASE), Timestamp.from(BASE));
        jdbcTemplate.update(
                """
                INSERT INTO test_case(id, test_suite_id, name, input, expected_action, severity, category, created_at, updated_at)
                VALUES (802, 800, 'case', 'input', 'ALLOW', 'HIGH', 'cat', ?, ?)
                """,
                Timestamp.from(BASE), Timestamp.from(BASE));
        jdbcTemplate.update(
                """
                INSERT INTO test_case_snapshot(id, test_run_id, source_test_case_id, name, input, expected_action, severity, category, created_at)
                VALUES (803, 801, 802, 'case', 'input', 'ALLOW', 'HIGH', 'cat', ?)
                """,
                Timestamp.from(BASE));
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

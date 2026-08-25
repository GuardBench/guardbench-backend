package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * @see <a href="../../../../docs/decisions/0008-async-testrun-persistence-contract.md">ADR 0008</a>
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
class AsyncTestRunPersistenceSchemaIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-25T03:00:00Z");

    private TestRunPersistenceFixture fixture;

    @BeforeEach
    void resetDatabase(@Autowired JdbcTemplate jdbcTemplate) {
        fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
    }

    @Test
    @DisplayName("비동기 TestRun의 멱등성·Outbox·두 Claim 테이블을 생성한다")
    void createsAsyncTechnicalTables(@Autowired JdbcTemplate jdbcTemplate) {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                      'test_run_idempotency', 'outbox_event',
                      'test_run_resolution_claim', 'test_execution_claim'
                  )
                """,
                Integer.class
        );

        assertEquals(4, tableCount);
    }

    @Test
    @DisplayName("유효하지 않은 Idempotency 만료 시각과 resolution claim lease·시도 횟수는 저장하지 않는다")
    void rejectsInvalidIdempotencyAndResolutionClaimShapes(@Autowired JdbcTemplate jdbcTemplate) {
        insertTestRunFixture();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO test_run_idempotency(
                            idempotency_key, request_fingerprint, test_run_id, created_at, expires_at
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                        "run-1",
                        "a".repeat(64),
                        100L,
                        Timestamp.from(CREATED_AT),
                        Timestamp.from(CREATED_AT)
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO test_run_resolution_claim(
                            test_run_id, claim_token, lease_until, attempt_count, claimed_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        100L,
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        Timestamp.from(CREATED_AT),
                        -1,
                        Timestamp.from(CREATED_AT),
                        Timestamp.from(CREATED_AT)
                )
        );
    }

    @Test
    @DisplayName("허용하지 않은 execution claim 대상과 Outbox event 값은 저장하지 않는다")
    void rejectsInvalidExecutionClaimAndOutboxValues(@Autowired JdbcTemplate jdbcTemplate) {
        insertTestRunFixture();
        fixture.insertSnapshot(1000L, 100L, 11L, CREATED_AT);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO test_execution_claim(
                            snapshot_id, target_type, claim_token, lease_until, attempt_count, claimed_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        1000L,
                        "UNKNOWN",
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        Timestamp.from(EXPIRES_AT),
                        0,
                        Timestamp.from(CREATED_AT),
                        Timestamp.from(CREATED_AT)
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO outbox_event(
                            event_id, event_type, schema_version, payload, deduplication_key,
                            status, created_at, published_at
                        ) VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                        """,
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        "UnknownEvent",
                        2,
                        "{}",
                        "dedup-1",
                        "PENDING",
                        Timestamp.from(CREATED_AT),
                        Timestamp.from(CREATED_AT)
                )
        );
    }

    private void insertTestRunFixture() {
        fixture.insertTestSuite(10L, CREATED_AT);
        fixture.insertTestCase(11L, 10L, CREATED_AT);
        fixture.insertQueuedTestRun(100L, 10L, 1, CREATED_AT);
    }
}

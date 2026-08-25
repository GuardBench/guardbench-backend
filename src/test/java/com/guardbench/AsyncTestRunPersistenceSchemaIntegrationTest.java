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

import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class AsyncTestRunPersistenceSchemaIntegrationTest {

    private static final Timestamp CREATED_AT = Timestamp.from(Instant.parse("2026-08-25T00:00:00Z"));
    private static final Timestamp EXPIRES_AT = Timestamp.from(Instant.parse("2026-08-25T03:00:00Z"));

    @BeforeEach
    void resetDatabase(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_event, test_suite CASCADE");
    }

    @Test
    @DisplayName("ADR 0008 기술 테이블 네 개의 PK, FK와 index 대상이 생성된다")
    void createsApprovedTechnicalTables(@Autowired JdbcTemplate jdbcTemplate) {
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
    @DisplayName("Idempotency expiry와 claim lease·attempt 제약은 유효하지 않은 행을 거부한다")
    void rejectsInvalidIdempotencyAndClaimShapes(@Autowired JdbcTemplate jdbcTemplate) {
        insertTestRunFixture(jdbcTemplate);

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
                        CREATED_AT,
                        CREATED_AT
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
                        CREATED_AT,
                        -1,
                        CREATED_AT,
                        CREATED_AT
                )
        );
    }

    @Test
    @DisplayName("Execution claim target과 Outbox event shape은 승인된 값만 허용한다")
    void rejectsInvalidExecutionClaimAndOutboxValues(@Autowired JdbcTemplate jdbcTemplate) {
        insertTestRunFixture(jdbcTemplate);
        insertSnapshotFixture(jdbcTemplate);

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
                        EXPIRES_AT,
                        0,
                        CREATED_AT,
                        CREATED_AT
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
                        CREATED_AT,
                        CREATED_AT
                )
        );
    }

    private static void insertTestRunFixture(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO test_suite(id, name, description, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                10L,
                "suite",
                null,
                CREATED_AT,
                CREATED_AT
        );
        jdbcTemplate.update(
                """
                INSERT INTO test_case(
                    id, test_suite_id, name, input, expected_action, severity, category, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                11L,
                10L,
                "case",
                "input",
                "ALLOW",
                "HIGH",
                "category",
                CREATED_AT,
                CREATED_AT
        );
        jdbcTemplate.update(
                """
                INSERT INTO test_run(
                    id, test_suite_id, status, test_case_count, processed_test_case_count,
                    baseline_guardrail_id, baseline_version, candidate_guardrail_id,
                    candidate_requested_source, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                100L,
                10L,
                "QUEUED",
                1,
                0,
                "guardrail",
                "1",
                "guardrail",
                "DRAFT",
                CREATED_AT,
                CREATED_AT
        );
    }

    private static void insertSnapshotFixture(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO test_case_snapshot(
                    id, test_run_id, source_test_case_id, name, input,
                    expected_action, severity, category, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1000L,
                100L,
                11L,
                "case",
                "input",
                "ALLOW",
                "HIGH",
                "category",
                CREATED_AT
        );
    }
}

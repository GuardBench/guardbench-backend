package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testrun.application.port.out.IdempotencyPort;
import com.guardbench.testrun.application.port.out.IdempotencyRecord;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class IdempotencyAdapterIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-08-25T00:00:00Z");
    private static final String FINGERPRINT_A = "a".repeat(64);
    private static final String FINGERPRINT_B = "b".repeat(64);

    @Autowired
    private IdempotencyPort idempotencyPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE test_run_idempotency CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE test_suite CASCADE");
        jdbcTemplate.update(
                "INSERT INTO test_suite(id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
                900L, "suite", Timestamp.from(BASE), Timestamp.from(BASE));
        jdbcTemplate.update(
                """
                INSERT INTO test_run(id, test_suite_id, status, test_case_count, processed_test_case_count,
                    baseline_guardrail_id, baseline_version, candidate_guardrail_id,
                    candidate_requested_source, created_at, updated_at)
                VALUES (901, 900, 'QUEUED', 1, 0, 'g1', '1', 'g1', 'DRAFT', ?, ?)
                """,
                Timestamp.from(BASE), Timestamp.from(BASE));
        jdbcTemplate.update(
                """
                INSERT INTO test_run(id, test_suite_id, status, test_case_count, processed_test_case_count,
                    baseline_guardrail_id, baseline_version, candidate_guardrail_id,
                    candidate_requested_source, created_at, updated_at)
                VALUES (902, 900, 'QUEUED', 1, 0, 'g1', '1', 'g1', 'DRAFT', ?, ?)
                """,
                Timestamp.from(BASE), Timestamp.from(BASE));
    }

    private Instant nowPlus3Hours() {
        return Instant.now().plus(3, ChronoUnit.HOURS);
    }

    @Test
    @DisplayName("같은 key와 같은 fingerprint 재요청은 기존 레코드를 반환한다")
    void sameKeyAndFingerprint_returnsExisting() {
        Instant expiresAt = nowPlus3Hours();
        IdempotencyRecord record = new IdempotencyRecord(
                "key-1", FINGERPRINT_A, 901, Instant.now(), expiresAt);
        idempotencyPort.save(record);

        Optional<IdempotencyRecord> found = idempotencyPort.findActiveByKey("key-1");
        assertTrue(found.isPresent());
        assertEquals(FINGERPRINT_A, found.get().requestFingerprint());
        assertEquals(901, found.get().testRunId());
    }

    @Test
    @DisplayName("같은 key와 다른 fingerprint는 기존 레코드를 찾아 충돌을 감지할 수 있다")
    void sameKeyDifferentFingerprint_conflictDetectable() {
        Instant now = Instant.now();
        Instant expiresAt = nowPlus3Hours();
        IdempotencyRecord original = new IdempotencyRecord(
                "key-2", FINGERPRINT_A, 901, now, expiresAt);
        idempotencyPort.save(original);

        // 다른 fingerprint로 save를 시도해도 기존이 만료되지 않았으므로 기존 row는 그대로다
        IdempotencyRecord conflict = new IdempotencyRecord(
                "key-2", FINGERPRINT_B, 902, now, expiresAt);
        idempotencyPort.save(conflict);

        Optional<IdempotencyRecord> found = idempotencyPort.findActiveByKey("key-2");
        assertTrue(found.isPresent());
        // 기존 만료 전이므로 원래 fingerprint와 testRunId 유지
        assertEquals(FINGERPRINT_A, found.get().requestFingerprint());
        assertEquals(901, found.get().testRunId());
    }

    @Test
    @DisplayName("만료된 key는 findActiveByKey에서 조회되지 않으며 새 레코드로 재사용할 수 있다")
    void expiredKey_canBeReused() {
        // 즉시 만료되는 row를 직접 INSERT (expires_at을 과거로 설정, CHECK expires_at > created_at 유지)
        jdbcTemplate.update(
                """
                INSERT INTO test_run_idempotency (idempotency_key, request_fingerprint, test_run_id, created_at, expires_at)
                VALUES (?, ?, ?, clock_timestamp() - INTERVAL '2 seconds', clock_timestamp() - INTERVAL '1 second')
                """,
                "key-expired", FINGERPRINT_A, 901);

        // 만료 row는 조회되지 않음
        Optional<IdempotencyRecord> found = idempotencyPort.findActiveByKey("key-expired");
        assertTrue(found.isEmpty());

        // 만료된 key에 새 레코드 저장 가능
        Instant now = Instant.now();
        IdempotencyRecord reuse = new IdempotencyRecord(
                "key-expired", FINGERPRINT_B, 902, now, nowPlus3Hours());
        idempotencyPort.save(reuse);

        Optional<IdempotencyRecord> reused = idempotencyPort.findActiveByKey("key-expired");
        assertTrue(reused.isPresent());
        assertEquals(FINGERPRINT_B, reused.get().requestFingerprint());
        assertEquals(902, reused.get().testRunId());
    }
}

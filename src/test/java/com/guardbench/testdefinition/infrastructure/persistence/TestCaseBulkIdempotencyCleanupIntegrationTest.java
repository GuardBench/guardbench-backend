package com.guardbench.testdefinition.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testdefinition.application.TestCaseBulkIdempotencyCleanupService;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest(properties = "guardbench.test-case-bulk-idempotency.cleanup.enabled=false")
@Import(PostgresTestConfiguration.class)
class TestCaseBulkIdempotencyCleanupIntegrationTest {

    private static final long TEST_SUITE_ID = 990_253L;

    @Autowired
    private TestCaseBulkIdempotencyCleanupService cleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        clearRows();
        jdbcTemplate.update("""
                INSERT INTO test_suite(id, name, description, created_at, updated_at)
                VALUES (?, 'cleanup suite', NULL, clock_timestamp(), clock_timestamp())
                """, TEST_SUITE_ID);
    }

    @AfterEach
    void tearDown() {
        clearRows();
    }

    @Test
    @DisplayName("한 batch 한도만큼 만료 레코드를 삭제하고 활성 레코드는 보존한다")
    void deletesOnlyExpiredRowsWithinBatchLimit() {
        insertExpiredRows("limited", 5);
        insertActiveRow("active");

        assertThat(cleanupService.deleteExpiredBatch(2)).isEqualTo(2);

        assertThat(countExpired()).isEqualTo(3);
        assertThat(countByKey("active")).isOne();
    }

    @Test
    @DisplayName("다른 트랜잭션이 처리 중인 만료 claim은 건너뛰고 잠금 해제 후 삭제한다")
    void skipsLockedClaimUntilNextBatch() throws Exception {
        insertExpiredRows("locked", 1);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT idempotency_key
                    FROM test_case_bulk_idempotency
                    WHERE idempotency_key = 'locked-1'
                    FOR UPDATE
                    """)) {
                statement.executeQuery();
                assertThat(cleanupService.deleteExpiredBatch(10)).isZero();
            } finally {
                connection.rollback();
            }
        }

        assertThat(cleanupService.deleteExpiredBatch(10)).isOne();
        assertThat(countByKey("locked-1")).isZero();
    }

    @Test
    @DisplayName("다중 인스턴스의 동시 cleanup은 batch를 중복 삭제하지 않고 전체 건수로 수렴한다")
    void concurrentCleanupIsIdempotent() throws Exception {
        insertExpiredRows("concurrent", 100);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> deleteAfter(start, 60)),
                    executor.submit(() -> deleteAfter(start, 60)));
            start.countDown();

            int firstDeleted = futures.get(0).get(30, TimeUnit.SECONDS);
            int secondDeleted = futures.get(1).get(30, TimeUnit.SECONDS);

            assertThat(firstDeleted).isBetween(0, 60);
            assertThat(secondDeleted).isBetween(0, 60);
            assertThat(firstDeleted + secondDeleted).isEqualTo(100);
            assertThat(countExpired()).isZero();
            assertThat(cleanupService.deleteExpiredBatch(60)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private int deleteAfter(CountDownLatch start, int batchSize) throws InterruptedException {
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return cleanupService.deleteExpiredBatch(batchSize);
    }

    private void insertExpiredRows(String prefix, int count) {
        jdbcTemplate.update("""
                INSERT INTO test_case_bulk_idempotency (
                    idempotency_key, request_fingerprint, test_suite_id,
                    created_test_case_ids, total_test_case_count, created_at, expires_at)
                SELECT ? || '-' || sequence_number,
                       repeat('a', 64),
                       ?,
                       ARRAY[sequence_number]::bigint[],
                       1,
                       clock_timestamp() - INTERVAL '4 hours',
                       clock_timestamp() - INTERVAL '1 hour'
                FROM generate_series(1, ?) AS sequence_number
                """, prefix, TEST_SUITE_ID, count);
    }

    private void insertActiveRow(String key) {
        jdbcTemplate.update("""
                INSERT INTO test_case_bulk_idempotency (
                    idempotency_key, request_fingerprint, test_suite_id,
                    created_test_case_ids, total_test_case_count, created_at, expires_at)
                VALUES (?, repeat('b', 64), ?, ARRAY[1]::bigint[], 1,
                        clock_timestamp(), clock_timestamp() + INTERVAL '3 hours')
                """, key, TEST_SUITE_ID);
    }

    private int countExpired() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM test_case_bulk_idempotency
                WHERE expires_at <= clock_timestamp()
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private int countByKey(String key) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM test_case_bulk_idempotency WHERE idempotency_key = ?",
                Integer.class,
                key);
        return count == null ? 0 : count;
    }

    private void clearRows() {
        jdbcTemplate.update("DELETE FROM test_case_bulk_idempotency WHERE test_suite_id = ?", TEST_SUITE_ID);
        jdbcTemplate.update("DELETE FROM test_suite WHERE id = ?", TEST_SUITE_ID);
    }
}

package com.guardbench.testdefinition.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testdefinition.application.port.out.TestCaseBulkIdempotencyPort;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestCaseListCriteria;
import com.guardbench.testdefinition.application.query.TestCaseSummary;
import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testsupport.PostgresTestConfiguration;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestCaseServiceIntegrationTest {

    @Autowired
    private TestCaseService service;

    @Autowired
    private TestSuiteService testSuiteService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private TestCaseBulkIdempotencyPort bulkIdempotencyPort;

    @Test
    @DisplayName("TestCase를 생성하고 상세 조회한 뒤 정의를 원자적으로 수정한다")
    void createsGetsAndUpdatesTestCase() {
        long suiteId = createSuite();
        TestCaseDetail created = service.create(suiteId, createCommand());

        TestCaseDetail updated = service.update(created.id(), new TestCaseUpdateCommand(
                true, "수정 이름", false, null, false, null,
                true, Severity.HIGH, true, "PRIVACY"));

        assertEquals("수정 이름", updated.name());
        assertEquals(Severity.HIGH, updated.severity());
        assertEquals("PRIVACY", service.get(created.id()).category());
    }

    @Test
    @DisplayName("물리 삭제 후 상세 조회는 404이고 현재 목록에서 제외된다")
    void deletionReturnsNotFoundAndExcludesCurrentList() {
        long suiteId = createSuite();
        TestCaseDetail created = service.create(suiteId, createCommand());

        service.delete(created.id());

        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> service.get(created.id()));
        PageResult<TestCaseSummary> result = service.list(
                suiteId,
                TestCaseListCriteria.firstPage(new com.guardbench.testdefinition.domain.TestSuiteId(suiteId)));
        assertEquals(ApplicationErrorCode.TEST_CASE_NOT_FOUND, exception.errorCode());
        assertTrue(result.items().isEmpty());
    }

    @Test
    @DisplayName("TestCase 삭제 후에도 과거 TestRun과 Snapshot을 보존한다")
    void deletionPreservesHistoricalRunAndSnapshot() {
        long suiteId = createSuite();
        TestCaseDetail created = service.create(suiteId, createCommand());
        TestRunPersistenceFixture fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.insertQueuedTestRun(
                910_001L, suiteId, 1, Instant.parse("2026-08-26T00:00:00Z"));
        fixture.insertSnapshot(
                920_001L, 910_001L, created.id(), Instant.parse("2026-08-26T00:00:00Z"));

        service.delete(created.id());

        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_run WHERE id = 910001", Long.class));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_case_snapshot WHERE id = 920001", Long.class));
        assertEquals(created.id(), jdbcTemplate.queryForObject(
                "SELECT source_test_case_id FROM test_case_snapshot WHERE id = 920001", Long.class));
    }

    @Test
    @DisplayName("존재하지 않는 부모 TestSuite에는 TestCase를 생성하지 않는다")
    void createRequiresExistingSuite() {
        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> service.create(999_999L, createCommand()));

        assertEquals(ApplicationErrorCode.TEST_SUITE_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("1~1000개의 TestCase를 원자적으로 생성하고 생성 ID와 갱신된 전체 건수를 반환한다")
    void bulkCreateReturnsCreatedIdsAndUpdatedTotalCount() {
        long suiteId = createSuite();
        service.create(suiteId, createCommand());

        TestCaseBulkCreateResult result = service.createBulk(
                suiteId,
                new TestCaseBulkCreateCommand(
                        "bulk-key-1",
                        List.of(createCommand(), new TestCaseCreateCommand(
                                "Prompt Injection 차단", "지시를 무시해", Action.BLOCK,
                                Severity.HIGH, "PROMPT_INJECTION"))));

        assertEquals(2, result.createdCount());
        assertEquals(3, result.totalTestCaseCount());
        assertEquals(3, service.list(
                suiteId,
                TestCaseListCriteria.firstPage(
                        new com.guardbench.testdefinition.domain.TestSuiteId(suiteId)))
                .totalElements());
    }

    @Test
    @DisplayName("같은 Idempotency-Key와 같은 일괄 요청은 기존 결과를 반환하고 중복 생성하지 않는다")
    void bulkCreateReusesSameRequestWithoutDuplicates() {
        long suiteId = createSuite();
        TestCaseBulkCreateCommand command = new TestCaseBulkCreateCommand(
                "bulk-key-2", List.of(createCommand(), createCommand()));

        TestCaseBulkCreateResult first = service.createBulk(suiteId, command);
        TestCaseBulkCreateResult replayed = service.createBulk(suiteId, command);

        assertEquals(first, replayed);
        assertEquals(2, replayed.totalTestCaseCount());
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_case WHERE test_suite_id = ?", Long.class, suiteId));
    }

    @Test
    @DisplayName("활성 판정 직후 key가 만료되면 claim을 다시 획득해 정상 생성한다")
    void bulkCreateRetriesClaimAcrossExpirationBoundary() {
        long suiteId = createSuite();
        String idempotencyKey = "bulk-key-expiration-boundary";
        TestCaseBulkCreateCommand command = new TestCaseBulkCreateCommand(
                idempotencyKey, List.of(createCommand()));
        TestCaseBulkCreateResult first = service.createBulk(suiteId, command);
        AtomicBoolean expireBeforeRead = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (expireBeforeRead.compareAndSet(true, false)) {
                jdbcTemplate.update("""
                        UPDATE test_case_bulk_idempotency
                        SET expires_at = clock_timestamp() - INTERVAL '1 microsecond'
                        WHERE idempotency_key = ?
                        """, idempotencyKey);
            }
            return invocation.callRealMethod();
        }).when(bulkIdempotencyPort).findActiveByKey(idempotencyKey);

        TestCaseBulkCreateResult renewed = service.createBulk(suiteId, command);

        assertNotEquals(first.createdTestCaseIds(), renewed.createdTestCaseIds());
        assertEquals(2, renewed.totalTestCaseCount());
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_case WHERE test_suite_id = ?", Long.class, suiteId));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 Idempotency-Key의 동시 요청은 한 번만 생성되고 같은 결과로 수렴한다")
    void concurrentBulkCreateWithSameKeyCreatesOnce() throws Exception {
        long suiteId = createSuite();
        TestCaseBulkCreateCommand command = new TestCaseBulkCreateCommand(
                "bulk-key-concurrent", List.of(createCommand(), createCommand()));
        int workers = 2;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            List<Future<TestCaseBulkCreateResult>> futures = java.util.stream.IntStream
                    .range(0, workers)
                    .mapToObj(ignored -> executor.submit(() -> {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        return service.createBulk(suiteId, command);
                    }))
                    .toList();

            start.countDown();
            TestCaseBulkCreateResult first = futures.get(0).get(60, TimeUnit.SECONDS);
            TestCaseBulkCreateResult second = futures.get(1).get(60, TimeUnit.SECONDS);

            assertEquals(first, second);
            assertEquals(2L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM test_case WHERE test_suite_id = ?", Long.class, suiteId));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM test_case_bulk_idempotency WHERE idempotency_key = ?",
                    Long.class,
                    command.idempotencyKey()));
        } finally {
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM test_case WHERE test_suite_id = ?", suiteId);
            jdbcTemplate.update("DELETE FROM test_suite WHERE id = ?", suiteId);
        }
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 다른 일괄 요청에 재사용하면 409 충돌로 거부한다")
    void bulkCreateRejectsReusedKeyWithDifferentRequest() {
        long suiteId = createSuite();
        service.createBulk(suiteId, new TestCaseBulkCreateCommand(
                "bulk-key-3", List.of(createCommand())));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.createBulk(suiteId, new TestCaseBulkCreateCommand(
                        "bulk-key-3",
                        List.of(new TestCaseCreateCommand(
                                "다른 케이스", "다른 입력", Action.ALLOW,
                                Severity.LOW, "OTHER")))));

        assertEquals(ApplicationErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.errorCode());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_case WHERE test_suite_id = ?", Long.class, suiteId));
    }

    @Test
    @DisplayName("존재하지 않는 TestSuite의 일괄 요청은 생성과 멱등성 기록 없이 404로 거부한다")
    void bulkCreateRequiresExistingSuiteBeforeClaimingKey() {
        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.createBulk(999_999L, new TestCaseBulkCreateCommand(
                        "bulk-key-missing-suite", List.of(createCommand()))));

        assertEquals(ApplicationErrorCode.TEST_SUITE_NOT_FOUND, exception.errorCode());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_case_bulk_idempotency", Long.class));
    }

    private long createSuite() {
        return testSuiteService.create(new TestSuiteCreateCommand("Suite", null, List.of())).id();
    }

    private TestCaseCreateCommand createCommand() {
        return new TestCaseCreateCommand(
                "PII 차단", "개인정보", Action.BLOCK, Severity.CRITICAL, "PII");
    }
}

package com.guardbench.testdefinition.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestCaseId;
import com.guardbench.testdefinition.domain.TestSuite;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestCaseRepository;
import com.guardbench.testdefinition.domain.repository.TestSuiteRepository;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * ADR 0009의 PostgreSQL READ COMMITTED 조건부 삭제 경쟁을 실제 트랜잭션으로 검증한다.
 *
 * @see <a href="file:../docs/decisions/0009-testcase-soft-delete-concurrency.md">ADR 0009</a>
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
class TestCaseSoftDeleteConcurrencyIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant FIRST_DELETED_AT = Instant.parse("2026-08-25T11:00:00Z");
    private static final Instant SECOND_DELETED_AT = Instant.parse("2026-08-25T12:00:00Z");

    @Autowired
    private TestCaseRepository repository;

    @Autowired
    private TestSuiteRepository testSuiteRepository;

    @Autowired
    private TestCaseJpaRepository jpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestSuiteId suiteId;
    private TestCaseId testCaseId;

    @AfterEach
    void deleteFixture() {
        if (testCaseId != null) {
            jdbcTemplate.update("DELETE FROM test_case WHERE id = ?", testCaseId.value());
        }
        if (suiteId != null) {
            jdbcTemplate.update("DELETE FROM test_suite WHERE id = ?", suiteId.value());
        }
    }

    @Test
    @DisplayName("동시 삭제 두 건은 정확히 하나만 성공하고 나머지는 TEST_CASE_NOT_FOUND다")
    void allowsExactlyOneOfConcurrentDeletions() throws Exception {
        storeActiveTestCase();
        CountDownLatch loaded = new CountDownLatch(2);
        CountDownLatch startUpdate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TestCase> first = executor.submit(
                    () -> deleteInTransaction(FIRST_DELETED_AT, loaded, startUpdate));
            Future<TestCase> second = executor.submit(
                    () -> deleteInTransaction(SECOND_DELETED_AT, loaded, startUpdate));

            assertTrue(loaded.await(10, TimeUnit.SECONDS));
            startUpdate.countDown();

            List<Future<TestCase>> futures = List.of(first, second);
            long successCount = futures.stream().filter(this::completedSuccessfully).count();
            List<Throwable> failures = futures.stream()
                    .filter(future -> !completedSuccessfully(future))
                    .map(this::failureCause)
                    .toList();

            assertEquals(1L, successCount);
            assertEquals(1, failures.size());
            ApplicationException failure = assertInstanceOf(
                    ApplicationException.class, failures.getFirst());
            assertEquals(ApplicationErrorCode.TEST_CASE_NOT_FOUND, failure.errorCode());
        }

        TestCase stored = jpaRepository.findById(testCaseId.value())
                .map(TestCaseEntityMapper::toDomain)
                .orElseThrow();
        assertTrue(stored.isDeleted());
        assertEquals(stored.deletedAt(), stored.updatedAt());
        assertTrue(List.of(FIRST_DELETED_AT, SECOND_DELETED_AT).contains(stored.deletedAt()));
    }

    private void storeActiveTestCase() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            suiteId = testSuiteRepository.nextIdentity();
            testSuiteRepository.save(TestSuite.create(suiteId, "동시 삭제 Suite", null, CREATED_AT));
            testCaseId = repository.nextIdentity();
            repository.save(TestCase.create(
                    testCaseId, suiteId, "동시 삭제 대상", "input",
                    new ExpectedResult(Action.BLOCK), Severity.CRITICAL, "PII", CREATED_AT));
        });
    }

    private TestCase deleteInTransaction(
            Instant deletedAt,
            CountDownLatch loaded,
            CountDownLatch startUpdate) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        return transaction.execute(status -> {
            TestCase target = repository.findActiveById(testCaseId).orElseThrow();
            loaded.countDown();
            await(startUpdate);
            target.delete(deletedAt);

            return repository.save(target);
        });
    }

    private boolean completedSuccessfully(Future<TestCase> future) {
        try {
            future.get(10, TimeUnit.SECONDS);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private Throwable failureCause(Future<TestCase> future) {
        try {
            future.get(10, TimeUnit.SECONDS);
            throw new AssertionError("실패한 Future가 성공했습니다.");
        } catch (ExecutionException exception) {
            return exception.getCause();
        } catch (Exception exception) {
            return exception;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 삭제 시작 신호를 기다리는 시간이 초과됐습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 삭제 대기가 중단됐습니다.", exception);
        }
    }
}

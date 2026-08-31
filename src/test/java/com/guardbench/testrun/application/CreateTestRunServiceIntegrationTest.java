package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * TestRun 접수 유스케이스를 실제 PostgreSQL로 검증한다.
 *
 * <p>TestRun, Snapshot과 {@code TestRunRequested} Outbox 저장이 ADR 0005/0008에 따라 하나의
 * 트랜잭션으로 커밋되는지, Idempotency 판정이 실제 저장된 레코드를 기준으로 동작하는지 검증한다.
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
class CreateTestRunServiceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-26T00:00:00Z");

    private TestRunPersistenceFixture fixture;

    @BeforeEach
    void resetDatabase(@Autowired JdbcTemplate jdbcTemplate) {
        fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        fixture.insertTestSuite(900L, CREATED_AT);
        fixture.insertTestCase(901L, 900L, CREATED_AT);
        fixture.insertTestCase(902L, 900L, CREATED_AT);
    }

    @AfterEach
    void clearDatabase() {
        fixture.clearPersistenceTables();
    }

    @Test
    @DisplayName("접수에 성공하면 TestRun, 모든 활성 TestCase의 Snapshot과 TestRunRequested Outbox가 함께 커밋된다")
    void createsTestRunSnapshotsAndOutboxEventAtomically(
            @Autowired CreateTestRunService service,
            @Autowired JdbcTemplate jdbcTemplate) {
        TestRunCreateCommand command = new TestRunCreateCommand(
                900L, "HTTP_ENDPOINT", "https://example.com/chat", "v1", profile(), null);

        TestRunCreateResult result = service.create(command);

        assertEquals("QUEUED", result.status());
        assertEquals(2, result.testCaseCount());

        Integer runCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_run WHERE id = ?", Integer.class, result.id());
        assertEquals(1, runCount);

        Integer snapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_case_snapshot WHERE test_run_id = ?", Integer.class, result.id());
        assertEquals(2, snapshotCount);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE deduplication_key = ?",
                Integer.class, "TestRunRequested:" + result.id());
        assertEquals(1, outboxCount);
    }

    @Test
    @DisplayName("같은 Idempotency-Key와 같은 요청을 재전송하면 새 행을 만들지 않고 기존 TestRun을 반환한다")
    void reusesExistingTestRunAcrossRequestsWithSameKey(@Autowired CreateTestRunService service) {
        TestRunCreateCommand command = new TestRunCreateCommand(
                900L, "HTTP_ENDPOINT", "https://example.com/chat", "v1", profile(), "idem-key-integration-1");

        TestRunCreateResult first = service.create(command);
        TestRunCreateResult second = service.create(command);

        assertEquals(first.id(), second.id());
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 다른 요청에 재사용하면 새 TestRun을 만들지 않고 409로 거부한다")
    void rejectsDifferentRequestWithSameKey(@Autowired CreateTestRunService service, @Autowired JdbcTemplate jdbcTemplate) {
        fixture.insertTestSuite(910L, CREATED_AT);
        fixture.insertTestCase(911L, 910L, CREATED_AT);
        TestRunCreateCommand first = new TestRunCreateCommand(
                900L, "HTTP_ENDPOINT", "https://example.com/chat", "v1", profile(), "idem-key-integration-2");
        TestRunCreateCommand different = new TestRunCreateCommand(
                910L, "HTTP_ENDPOINT", "https://example.com/chat", "v1", profile(), "idem-key-integration-2");

        service.create(first);
        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.create(different));

        assertEquals(ApplicationErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.errorCode());
        Integer runCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_run WHERE test_suite_id = 910", Integer.class);
        assertEquals(0, runCount);
    }

    @Test
    @DisplayName("존재하지 않는 TestSuite로 접수하면 어떤 테이블에도 행을 남기지 않는다")
    void doesNotPersistAnythingWhenTestSuiteMissing(
            @Autowired CreateTestRunService service, @Autowired JdbcTemplate jdbcTemplate) {
        TestRunCreateCommand command = new TestRunCreateCommand(
                999L, "HTTP_ENDPOINT", "https://example.com/chat", "v1", profile(), null);

        assertThrows(ApplicationException.class, () -> service.create(command));

        Integer runCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_run", Integer.class);
        assertEquals(0, runCount);
        Integer outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);
        assertEquals(0, outboxCount);
    }

    @Test
    @DisplayName("활성 TestCase가 없는 TestSuite로 접수하면 TEST_SUITE_EMPTY로 거부하고 아무것도 저장하지 않는다")
    void rejectsEmptyTestSuiteWithoutPersisting(
            @Autowired CreateTestRunService service, @Autowired JdbcTemplate jdbcTemplate) {
        fixture.insertTestSuite(920L, CREATED_AT);
        TestRunCreateCommand command = new TestRunCreateCommand(
                920L, "HTTP_ENDPOINT", "https://example.com/chat", "v1", profile(), null);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.create(command));

        assertEquals(ApplicationErrorCode.TEST_SUITE_EMPTY, exception.errorCode());
        Integer runCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_run WHERE test_suite_id = 920", Integer.class);
        assertEquals(0, runCount);
    }

    @Test
    @DisplayName("접수된 TestRun은 QUEUED 상태와 접수 트랜잭션에서 고정된 TestCaseSnapshot 개수를 갖는다")
    void createdTestRunHasQueuedStatusAndFixedTestCaseCount(
            @Autowired CreateTestRunService service, @Autowired JdbcTemplate jdbcTemplate) {
        TestRunCreateCommand command = new TestRunCreateCommand(
                900L, "HTTP_ENDPOINT", "https://example.com/chat", "v1", profile(), null);

        TestRunCreateResult result = service.create(command);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM test_run WHERE id = ?", String.class, result.id());
        Integer testCaseCount = jdbcTemplate.queryForObject(
                "SELECT test_case_count FROM test_run WHERE id = ?", Integer.class, result.id());
        assertEquals("QUEUED", status);
        assertEquals(2, testCaseCount);
        assertTrue(result.createdAt() != null);
    }

    private static com.guardbench.testrun.domain.EvaluationProfile profile() {
        return new com.guardbench.testrun.domain.EvaluationProfile(List.of("PROMPT_INJECTION"), "STANDARD");
    }
}

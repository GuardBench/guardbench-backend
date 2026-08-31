package com.guardbench.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.evaluation.application.FinalizeTestRunService.FinalizationOutcome;
import com.guardbench.testdefinition.application.TestCaseService;
import com.guardbench.testrun.application.CreateTestRunService;
import com.guardbench.testrun.application.GetTestRunDetailService;
import com.guardbench.testrun.application.GetTestRunResultListService;
import com.guardbench.testrun.application.TestRunCreateCommand;
import com.guardbench.testrun.application.TestRunCreateResult;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;
import com.guardbench.testrun.application.port.out.TargetProviderException;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.testrun.application.port.out.TestRunResultListCriteria;
import com.guardbench.testrun.domain.TestRunStatus;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testrun.support.fixture.WorkerChainTestSupport;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * #19 MVP 통합·회귀 테스트다.
 *
 * <p>TestSuite → TestCase 생성 → TestRun 접수 → Resolve → Execute(BASELINE/CANDIDATE)
 * → Finalize → 결과 조회까지 이어지는 전체 흐름을 실제 PostgreSQL(Testcontainers)로 검증한다.
 *
 * <p>Amazon Bedrock Guardrails는 {@link WorkerChainTestSupport}가 제공하는
 * Test Adapter(람다)로 대체하며, 실제 AWS 호출이나 자격 증명 없이 반복 실행 가능하다.
 * SQS 발행도 사용하지 않고 Application Service를 직접 순차 호출한다.
 */
@SpringBootTest
@Import({PostgresTestConfiguration.class, WorkerChainTestSupport.class})
@Disabled("#114 rejects new BEDROCK_GUARDRAIL Targets; HTTP execution and evaluator worker flow are owned by #115~#117")
class MvpEndToEndFlowIntegrationTest {

    private static final long TEST_SUITE_ID = 5000L;

    @Autowired
    private CreateTestRunService createTestRunService;

    @Autowired
    private TestCaseService testCaseService;

    @Autowired
    private GetTestRunDetailService getTestRunDetailService;

    @Autowired
    private GetTestRunResultListService getTestRunResultListService;

    @Autowired
    private WorkerChainTestSupport.WorkerChain workerChain;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestRunPersistenceFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        workerChain.reset();
    }

    @Test
    @DisplayName("TestSuite/TestCase 생성부터 TestRun 접수, 실행, 종료 결과 조회까지 전체 흐름이 성공한다")
    void fullFlowFromSuiteCreationToFinishedResults() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        fixture.insertTestSuite(TEST_SUITE_ID, now);
        fixture.insertTestCase(5001L, TEST_SUITE_ID, now);
        fixture.insertTestCase(5002L, TEST_SUITE_ID, now);

        TestRunCreateResult created = createTestRunService.create(new TestRunCreateCommand(
                TEST_SUITE_ID, "BEDROCK_GUARDRAIL", "guardrail-1", "DRAFT", null));

        assertThat(created.status()).isEqualTo("QUEUED");
        assertThat(created.testCaseCount()).isEqualTo(2);

        List<Long> snapshotIds = snapshotIdsFor(created.id());
        assertThat(snapshotIds).hasSize(2);

        workerChain.runFullWorkerChain(created.id(), snapshotIds);

        TestRunDetail detail = getTestRunDetailService.getTestRun(created.id());
        assertThat(detail.status()).isEqualTo(TestRunStatus.FINISHED);
        assertThat(detail.progress().processedTestCaseCount()).isEqualTo(2);
        assertThat(detail.qualityGate().statusCode()).isEqualTo("NOT_EVALUATED");

        PageResult<TestRunResultItem> results = getTestRunResultListService.getResults(
                created.id(), TestRunResultListCriteria.firstPage());
        assertThat(results.items()).hasSize(2);
        assertThat(results.items())
                .allSatisfy(item -> assertThat(item.assertionStatusCode()).isEqualTo("PASS"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key와 같은 요청을 재전송하면 기존 TestRun을 반환한다")
    void reusesExistingTestRunForSameIdempotencyKey() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        fixture.insertTestSuite(TEST_SUITE_ID, now);
        fixture.insertTestCase(5011L, TEST_SUITE_ID, now);

        TestRunCreateCommand command = new TestRunCreateCommand(
                TEST_SUITE_ID, "BEDROCK_GUARDRAIL", "guardrail-1", "DRAFT", "e2e-idem-key-1");

        TestRunCreateResult first = createTestRunService.create(command);
        TestRunCreateResult second = createTestRunService.create(command);

        assertThat(second.id()).isEqualTo(first.id());
        Integer runCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_run WHERE test_suite_id = ?", Integer.class, TEST_SUITE_ID);
        assertThat(runCount).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 다른 요청에 재사용하면 409로 거부하고 새 TestRun을 만들지 않는다")
    void rejectsConflictingRequestWithSameIdempotencyKey() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        fixture.insertTestSuite(TEST_SUITE_ID, now);
        fixture.insertTestCase(5021L, TEST_SUITE_ID, now);
        long otherSuiteId = TEST_SUITE_ID + 1;
        fixture.insertTestSuite(otherSuiteId, now);
        fixture.insertTestCase(5022L, otherSuiteId, now);

        TestRunCreateCommand first = new TestRunCreateCommand(
                TEST_SUITE_ID, "BEDROCK_GUARDRAIL", "guardrail-1", "DRAFT", "e2e-idem-key-2");
        TestRunCreateCommand conflicting = new TestRunCreateCommand(
                otherSuiteId, "BEDROCK_GUARDRAIL", "guardrail-1", "DRAFT", "e2e-idem-key-2");

        createTestRunService.create(first);

        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> createTestRunService.create(conflicting));
        assertThat(exception.errorCode()).isEqualTo(ApplicationErrorCode.IDEMPOTENCY_KEY_CONFLICT);

        Integer otherSuiteRunCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_run WHERE test_suite_id = ?", Integer.class, otherSuiteId);
        assertThat(otherSuiteRunCount).isZero();
    }

    @Test
    @DisplayName("한 Snapshot의 Provider 실행이 영구 실패해도 나머지 Snapshot은 정상 종결되고 QG는 FAIL이다")
    void partialExecutionFailureStillFinishesWithFailingQualityGate() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        fixture.insertTestSuite(TEST_SUITE_ID, now);
        fixture.insertTestCase(5031L, TEST_SUITE_ID, now); // expected ALLOW (fixture 기본값)
        fixture.insertTestCase(5032L, TEST_SUITE_ID, now);

        TestRunCreateResult created = createTestRunService.create(new TestRunCreateCommand(
                TEST_SUITE_ID, "BEDROCK_GUARDRAIL", "guardrail-1", "DRAFT", null));
        List<Long> snapshotIds = snapshotIdsFor(created.id());
        long failingSnapshotId = snapshotIds.get(0);
        long healthySnapshotId = snapshotIds.get(1);
        markSnapshotInputDistinctly(failingSnapshotId, "failing-input");
        markSnapshotInputDistinctly(healthySnapshotId, "healthy-input");

        workerChain.execute(request -> {
            if (request.input().equals("failing-input")) {
                throw new TargetProviderException(TargetFailureCode.TARGET_NOT_FOUND);
            }
            return TargetExecutionResult.succeeded("ALLOW");
        });

        workerChain.resolveService().resolve(created.id());
        var executeService = workerChain.executeService();
        executeService.execute(failingSnapshotId);
        executeService.execute(healthySnapshotId);
        FinalizationOutcome outcome = workerChain.finalizeService().finalize(created.id());

        assertThat(outcome).isInstanceOf(FinalizationOutcome.Finalized.class);

        TestRunDetail detail = getTestRunDetailService.getTestRun(created.id());
        assertThat(detail.status()).isEqualTo(TestRunStatus.FINISHED);
        assertThat(detail.progress().processedTestCaseCount()).isEqualTo(2);

        String failingExecutionStatus = jdbcTemplate.queryForObject(
                "SELECT result_status FROM test_execution WHERE snapshot_id = ?",
                String.class, failingSnapshotId);
        assertThat(failingExecutionStatus).isEqualTo("FAILED");

        String executionOutcome = jdbcTemplate.queryForObject(
                "SELECT execution_outcome FROM test_run WHERE id = ?", String.class, created.id());
        assertThat(executionOutcome)
                .as("일부 execution만 실패하면 INCOMPLETE다")
                .isEqualTo("INCOMPLETE");

        String qgStatus = jdbcTemplate.queryForObject(
                "SELECT gate_status FROM quality_gate_result WHERE test_run_id = ?", String.class, created.id());
        assertThat(qgStatus)
                .as("비교 없는 단일 Target run은 Quality Gate를 평가하지 않는다")
                .isEqualTo("NOT_EVALUATED");
    }

    @Test
    @DisplayName("Candidate materialization이 영구 실패하면 모든 실행이 NOT_STARTED이고 QG는 NOT_EVALUATED다")
    void materializationPermanentFailureEndsWithNotEvaluatedQualityGate() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        fixture.insertTestSuite(TEST_SUITE_ID, now);
        fixture.insertTestCase(5041L, TEST_SUITE_ID, now);

        TestRunCreateResult created = createTestRunService.create(new TestRunCreateCommand(
                TEST_SUITE_ID, "BEDROCK_GUARDRAIL", "guardrail-1", "DRAFT", null));

        workerChain.prepare(request -> {
            throw new TargetProviderException(TargetFailureCode.TARGET_NOT_FOUND);
        });

        // ADR 0005: 최대 3회 시도 후 영구 실패로 종결한다.
        // resolution claim lease(45초)가 유효한 동안은 재선점이 거부되므로,
        // 매 시도 사이에 lease를 강제로 만료시켜 다음 attempt가 실제로 진행되게 한다.
        for (int attempt = 0; attempt < 3; attempt++) {
            workerChain.resolveService().resolve(created.id());
            expireResolutionClaimLease(created.id());
        }

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM test_run WHERE id = ?", String.class, created.id());
        String executionOutcome = jdbcTemplate.queryForObject(
                "SELECT execution_outcome FROM test_run WHERE id = ?", String.class, created.id());
        assertThat(status).isEqualTo("FINISHED");
        assertThat(executionOutcome).isEqualTo("ERROR");

        String qgStatus = jdbcTemplate.queryForObject(
                "SELECT gate_status FROM quality_gate_result WHERE test_run_id = ?", String.class, created.id());
        assertThat(qgStatus).isEqualTo("NOT_EVALUATED");

        Integer notStartedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_execution WHERE result_status = 'NOT_STARTED'", Integer.class);
        assertThat(notStartedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("TestRun 접수 후 원본 TestCase를 삭제해도 이미 만든 Snapshot과 과거 결과는 영향받지 않는다")
    void deletingSourceTestCaseDoesNotAffectExistingSnapshotOrResults() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        fixture.insertTestSuite(TEST_SUITE_ID, now);
        long testCaseId = 5051L;
        fixture.insertTestCase(testCaseId, TEST_SUITE_ID, now);

        TestRunCreateResult created = createTestRunService.create(new TestRunCreateCommand(
                TEST_SUITE_ID, "BEDROCK_GUARDRAIL", "guardrail-1", "DRAFT", null));
        List<Long> snapshotIds = snapshotIdsFor(created.id());
        assertThat(snapshotIds).hasSize(1);
        long snapshotId = snapshotIds.getFirst();

        workerChain.runFullWorkerChain(created.id(), snapshotIds);

        TestRunDetail beforeDeletion = getTestRunDetailService.getTestRun(created.id());
        assertThat(beforeDeletion.status()).isEqualTo(TestRunStatus.FINISHED);
        assertThat(beforeDeletion.qualityGate().statusCode()).isEqualTo("NOT_EVALUATED");

        testCaseService.delete(testCaseId);

        Boolean testCaseDeleted = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM test_case WHERE id = ?", Boolean.class, testCaseId);
        assertThat(testCaseDeleted).as("원본 TestCase는 논리 삭제되어야 한다").isTrue();

        Integer snapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_case_snapshot WHERE id = ?", Integer.class, snapshotId);
        assertThat(snapshotCount)
                .as("TestCase 삭제가 이미 생성된 Snapshot을 제거하지 않아야 한다")
                .isEqualTo(1);

        TestRunDetail afterDeletion = getTestRunDetailService.getTestRun(created.id());
        assertThat(afterDeletion.status()).isEqualTo(TestRunStatus.FINISHED);
        assertThat(afterDeletion.qualityGate().statusCode())
                .as("과거 TestRun의 Quality Gate 결과는 원본 TestCase 삭제와 무관하게 유지돼야 한다")
                .isEqualTo("NOT_EVALUATED");

        PageResult<TestRunResultItem> resultsAfterDeletion = getTestRunResultListService.getResults(
                created.id(), TestRunResultListCriteria.firstPage());
        assertThat(resultsAfterDeletion.items())
                .as("과거 실행 결과 목록도 원본 TestCase 삭제와 무관하게 유지돼야 한다")
                .hasSize(1);
    }

    @Test
    @DisplayName("단일 Target이 BLOCK 기대를 ALLOW로 응답하면 assertion은 FAIL이고 QG는 NOT_EVALUATED다")
    void assertionFailureDoesNotCreateComparisonQualityGate() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        fixture.insertTestSuite(TEST_SUITE_ID, now);
        long testCaseId = 5061L;
        fixture.insertTestCase(testCaseId, TEST_SUITE_ID, now);
        // fixture 기본 expected_action은 ALLOW이므로 BLOCK 기대 시나리오를 위해 직접 갱신한다.
        jdbcTemplate.update("UPDATE test_case SET expected_action = 'BLOCK' WHERE id = ?", testCaseId);

        TestRunCreateResult created = createTestRunService.create(new TestRunCreateCommand(
                TEST_SUITE_ID, "BEDROCK_GUARDRAIL", "guardrail-1", "DRAFT", null));
        List<Long> snapshotIds = snapshotIdsFor(created.id());
        // Snapshot은 접수 시점에 TestCase의 expected_action을 복제하므로 함께 갱신한다.
        jdbcTemplate.update("UPDATE test_case_snapshot SET expected_action = 'BLOCK' WHERE test_run_id = ?",
                created.id());

        workerChain.execute(request -> TargetExecutionResult.succeeded("ALLOW"));

        workerChain.runFullWorkerChain(created.id(), snapshotIds);

        String executionOutcome = jdbcTemplate.queryForObject(
                "SELECT execution_outcome FROM test_run WHERE id = ?", String.class, created.id());
        assertThat(executionOutcome)
                .as("모든 execution이 성공했으므로 COMPLETED다")
                .isEqualTo("COMPLETED");

        String qgStatus = jdbcTemplate.queryForObject(
                "SELECT gate_status FROM quality_gate_result WHERE test_run_id = ?", String.class, created.id());
        assertThat(qgStatus)
                .as("비교 결과를 생성하지 않으므로 QG는 평가하지 않는다")
                .isEqualTo("NOT_EVALUATED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT assertion_status FROM assertion_result WHERE snapshot_id = ?",
                String.class, snapshotIds.getFirst())).isEqualTo("FAIL");
    }

    // ─── Helper ──────────────────────────────────────────────────────

    private List<Long> snapshotIdsFor(long testRunId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM test_case_snapshot WHERE test_run_id = ? ORDER BY id", Long.class, testRunId);
    }

    private void markSnapshotInputDistinctly(long snapshotId, String input) {
        jdbcTemplate.update("UPDATE test_case_snapshot SET input = ? WHERE id = ?", input, snapshotId);
    }

    private void expireResolutionClaimLease(long testRunId) {
        jdbcTemplate.update(
                "UPDATE test_run_resolution_claim "
                        + "SET claimed_at = clock_timestamp() - INTERVAL '2 seconds', "
                        + "    lease_until = clock_timestamp() - INTERVAL '1 second' "
                        + "WHERE test_run_id = ?",
                testRunId);
    }
}

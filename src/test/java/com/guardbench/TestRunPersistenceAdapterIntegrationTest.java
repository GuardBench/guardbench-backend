package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testrun.application.port.out.NextTestCaseSnapshotIdPort;
import com.guardbench.testrun.application.port.out.NextTestRunIdPort;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.ActualResult;
import com.guardbench.testrun.domain.ApplicationResponse;
import com.guardbench.testrun.domain.EvaluationResult;
import com.guardbench.testrun.domain.ExpectedResult;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.SourceTestCaseId;
import com.guardbench.testrun.domain.SourceTestSuiteId;
import com.guardbench.testrun.domain.TargetReference;
import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.repository.TestCaseSnapshotRepository;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class TestRunPersistenceAdapterIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");

    private TestRunPersistenceFixture fixture;

    @BeforeEach
    void resetDatabase(@Autowired JdbcTemplate jdbcTemplate) {
        fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        fixture.insertTestSuite(500L, CREATED_AT);
        fixture.insertTestCase(501L, 500L, CREATED_AT);
    }

    @AfterEach
    void clearDatabase() {
        fixture.clearPersistenceTables();
    }

    @Test
    @DisplayName("TestRun과 Snapshot은 Application Clock 시각과 sequence 식별자를 손실 없이 저장·복원한다")
    void persistsTestRunAndSnapshot(
            @Autowired NextTestRunIdPort nextTestRunIdPort,
            @Autowired NextTestCaseSnapshotIdPort nextSnapshotIdPort,
            @Autowired TestRunRepository testRunRepository,
            @Autowired TestCaseSnapshotRepository snapshotRepository) {
        TestRunId runId = nextTestRunIdPort.nextId();
        fixture.insertTargetReference("target-ref-1");
        TestRun testRun = TestRun.queue(
                runId,
                new SourceTestSuiteId(500),
                new TargetReference("target-ref-1"),
                1,
                CREATED_AT
        );
        testRunRepository.save(testRun);

        TestCaseSnapshot snapshot = TestCaseSnapshot.of(
                nextSnapshotIdPort.nextId(), runId, new SourceTestCaseId(501),
                "case", "input", new ExpectedResult(Action.ALLOW), Severity.HIGH, "category", CREATED_AT
        );
        snapshotRepository.save(snapshot);

        assertEquals(testRun.status(), testRunRepository.findById(runId).orElseThrow().status());
        TestCaseSnapshot restored = snapshotRepository.findById(snapshot.id()).orElseThrow();
        assertEquals(snapshot.createdAt(), restored.createdAt());
        assertEquals(snapshot.expectedResult(), restored.expectedResult());
        assertTrue(runId.value() > 0);
        assertTrue(snapshot.id().value() > 0);
    }

    @Test
    @DisplayName("복합 PK TestExecution은 terminal 결과와 Application Clock lifecycle 시각을 저장·복원한다")
    void persistsTerminalExecution(
            @Autowired NextTestRunIdPort nextTestRunIdPort,
            @Autowired NextTestCaseSnapshotIdPort nextSnapshotIdPort,
            @Autowired TestRunRepository testRunRepository,
            @Autowired TestCaseSnapshotRepository snapshotRepository,
            @Autowired TestExecutionRepository executionRepository) {
        TestRunId runId = nextTestRunIdPort.nextId();
        fixture.insertTargetReference("target-ref-2");
        testRunRepository.save(TestRun.queue(
                runId, new SourceTestSuiteId(500), new TargetReference("target-ref-2"), 1, CREATED_AT));
        TestCaseSnapshot snapshot = TestCaseSnapshot.of(
                nextSnapshotIdPort.nextId(), runId, new SourceTestCaseId(501), "case", "input",
                new ExpectedResult(Action.ALLOW), Severity.HIGH, "category", CREATED_AT);
        snapshotRepository.save(snapshot);

        TestExecution execution = TestExecution.succeeded(
                new TestExecutionId(snapshot.id()),
                new ActualResult(Action.ALLOW),
                CREATED_AT.plusSeconds(1),
                CREATED_AT.plusSeconds(2));
        executionRepository.save(execution);

        TestExecution restored = executionRepository.findById(execution.id()).orElseThrow();
        assertEquals(execution.status(), restored.status());
        assertEquals(execution.actualResult(), restored.actualResult());
        assertEquals(execution.startedAt(), restored.startedAt());
        assertEquals(execution.completedAt(), restored.completedAt());
    }

    @Test
    @DisplayName("Application response와 Evaluator verdict를 분리해 저장·복원한다")
    void persistsSeparatedApplicationAndEvaluatorResults(
            @Autowired NextTestRunIdPort nextTestRunIdPort,
            @Autowired NextTestCaseSnapshotIdPort nextSnapshotIdPort,
            @Autowired TestRunRepository testRunRepository,
            @Autowired TestCaseSnapshotRepository snapshotRepository,
            @Autowired TestExecutionRepository executionRepository) {
        TestRunId runId = nextTestRunIdPort.nextId();
        fixture.insertTargetReference("target-ref-3");
        testRunRepository.save(TestRun.queue(
                runId, new SourceTestSuiteId(500), new TargetReference("target-ref-3"), 1, CREATED_AT));
        TestCaseSnapshot snapshot = TestCaseSnapshot.of(
                nextSnapshotIdPort.nextId(), runId, new SourceTestCaseId(501), "case", "input",
                new ExpectedResult(Action.BLOCK), Severity.HIGH, "category", CREATED_AT);
        snapshotRepository.save(snapshot);

        TestExecution execution = TestExecution.succeeded(
                new TestExecutionId(snapshot.id()),
                new ApplicationResponse("natural language response"),
                new EvaluationResult(Action.BLOCK),
                CREATED_AT.plusSeconds(1),
                CREATED_AT.plusSeconds(2));
        executionRepository.save(execution);

        TestExecution restored = executionRepository.findById(execution.id()).orElseThrow();
        assertEquals("natural language response", restored.applicationResponse().value());
        assertEquals(new EvaluationResult(Action.BLOCK), restored.evaluationResult());
        assertEquals(null, restored.error());
    }
}

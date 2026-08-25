package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;

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
import com.guardbench.testrun.domain.BaselineTarget;
import com.guardbench.testrun.domain.CandidateSource;
import com.guardbench.testrun.domain.CandidateTarget;
import com.guardbench.testrun.domain.ExpectedResult;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.SourceTestCaseId;
import com.guardbench.testrun.domain.SourceTestSuiteId;
import com.guardbench.testrun.domain.TargetType;
import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.repository.TestCaseSnapshotRepository;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class TestRunPersistenceAdapterIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");

    @BeforeEach
    void resetDatabase(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_event, test_suite CASCADE");
        jdbcTemplate.update(
                "INSERT INTO test_suite(id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
                500L, "suite", Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
        jdbcTemplate.update(
                """
                INSERT INTO test_case(id, test_suite_id, name, input, expected_action, severity, category, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                501L, 500L, "case", "input", "ALLOW", "HIGH", "category",
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
    }

    @Test
    @DisplayName("TestRun과 Snapshot은 Application Clock 시각과 sequence 식별자를 손실 없이 저장·복원한다")
    void persistsTestRunAndSnapshot(
            @Autowired NextTestRunIdPort nextTestRunIdPort,
            @Autowired NextTestCaseSnapshotIdPort nextSnapshotIdPort,
            @Autowired TestRunRepository testRunRepository,
            @Autowired TestCaseSnapshotRepository snapshotRepository) {
        TestRunId runId = nextTestRunIdPort.nextId();
        TestRun testRun = TestRun.queue(
                runId,
                new SourceTestSuiteId(500),
                new BaselineTarget("guardrail", "1"),
                new CandidateTarget("guardrail", CandidateSource.DRAFT, null),
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
        testRunRepository.save(TestRun.queue(
                runId, new SourceTestSuiteId(500), new BaselineTarget("guardrail", "1"),
                new CandidateTarget("guardrail", CandidateSource.DRAFT, null), 1, CREATED_AT));
        TestCaseSnapshot snapshot = TestCaseSnapshot.of(
                nextSnapshotIdPort.nextId(), runId, new SourceTestCaseId(501), "case", "input",
                new ExpectedResult(Action.ALLOW), Severity.HIGH, "category", CREATED_AT);
        snapshotRepository.save(snapshot);

        TestExecution execution = TestExecution.succeeded(
                new TestExecutionId(snapshot.id(), TargetType.BASELINE),
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
}

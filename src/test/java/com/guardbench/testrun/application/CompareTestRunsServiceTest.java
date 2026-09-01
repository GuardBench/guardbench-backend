package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.CompareStoredRegressionPort;
import com.guardbench.testrun.application.port.out.LoadTestRunRegressionPort;
import com.guardbench.testrun.application.port.out.PageCriteria;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.RegressionChangeView;
import com.guardbench.testrun.application.port.out.TargetReferenceView;
import com.guardbench.testrun.application.port.out.TestRunComparison;
import com.guardbench.testrun.application.port.out.TestRunRegressionSnapshot;
import com.guardbench.testrun.application.port.out.TestRunRegressionView;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.TestRunStatus;

import org.junit.jupiter.api.Test;

class CompareTestRunsServiceTest {

    private static final TargetReferenceView TARGET =
            new TargetReferenceView("target", "HTTP_ENDPOINT", "https://example.com/chat", null, "model");

    @Test
    void comparesStoredVerdictsWithoutExternalCalls() {
        TestRunRegressionView current = run(2L, "evaluator|guardrail|7");
        TestRunRegressionView comparison = run(1L, "evaluator|guardrail|7", "2026-08-25T09:00:00Z");
        TestRunRegressionSnapshot currentSnapshot = snapshot(101L, Action.BLOCK);
        TestRunRegressionSnapshot comparisonSnapshot = snapshot(201L, Action.ALLOW);
        LoadTestRunRegressionPort data = new FakeRegressionPort(
                List.of(current, comparison), List.of(currentSnapshot), List.of(comparisonSnapshot), 2L);
        CompareStoredRegressionPort comparisonPort = cases -> List.of(
                new RegressionChangeView(11L, "COMPARABLE", "SECURITY_REGRESSION"));

        TestRunComparison result = new CompareTestRunsService(data, comparisonPort).compare(2L, 1L);

        assertEquals(1L, result.regressedCount());
        assertEquals("SECURITY_REGRESSION", result.items().getFirst().changeType());
    }

    @Test
    void rejectsDifferentSnapshotDefinitions() {
        TestRunRegressionView current = run(2L, "evaluator|guardrail|7");
        TestRunRegressionView comparison = run(1L, "evaluator|guardrail|7", "2026-08-25T09:00:00Z");
        TestRunRegressionSnapshot currentSnapshot = snapshot(101L, Action.BLOCK);
        TestRunRegressionSnapshot comparisonSnapshot = new TestRunRegressionSnapshot(
                201L, 11L, "changed", "input", Action.BLOCK, Severity.HIGH, "PII", Action.ALLOW);
        LoadTestRunRegressionPort data = new FakeRegressionPort(
                List.of(current, comparison), List.of(currentSnapshot), List.of(comparisonSnapshot), 2L);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> new CompareTestRunsService(data, cases -> List.of()).compare(2L, 1L));

        assertEquals(ApplicationErrorCode.TEST_RUNS_NOT_COMPARABLE, exception.errorCode());
    }

    @Test
    void rejectsComparisonRunCompletedAfterCurrentRun() {
        TestRunRegressionView current = run(1L, "evaluator|guardrail|7", "2026-08-25T10:00:00Z");
        TestRunRegressionView comparison = run(2L, "evaluator|guardrail|7", "2026-08-25T11:00:00Z");
        LoadTestRunRegressionPort data = new FakeRegressionPort(
                List.of(current, comparison), List.of(snapshot(101L, Action.BLOCK)),
                List.of(snapshot(201L, Action.ALLOW)), 1L);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> new CompareTestRunsService(data, cases -> List.of()).compare(1L, 2L));

        assertEquals(ApplicationErrorCode.TEST_RUNS_NOT_COMPARABLE, exception.errorCode());
    }

    private static TestRunRegressionView run(long id, String evaluatorKey) {
        return run(id, evaluatorKey, "2026-08-25T10:00:00Z");
    }

    private static TestRunRegressionView run(long id, String evaluatorKey, String completedAt) {
        return new TestRunRegressionView(id, 10L, TestRunStatus.FINISHED, TARGET, null, evaluatorKey,
                Instant.parse(completedAt));
    }

    private static TestRunRegressionSnapshot snapshot(long id, Action verdict) {
        return new TestRunRegressionSnapshot(id, 11L, "case", "input", Action.BLOCK, Severity.HIGH, "PII", verdict);
    }

    private static final class FakeRegressionPort implements LoadTestRunRegressionPort {
        private final List<TestRunRegressionView> runs;
        private final List<TestRunRegressionSnapshot> currentSnapshots;
        private final List<TestRunRegressionSnapshot> comparisonSnapshots;

        private FakeRegressionPort(List<TestRunRegressionView> runs,
                                   List<TestRunRegressionSnapshot> currentSnapshots,
                                   List<TestRunRegressionSnapshot> comparisonSnapshots,
                                   long currentRunId) {
            this.runs = runs;
            this.currentSnapshots = currentSnapshots;
            this.comparisonSnapshots = comparisonSnapshots;
            this.currentRunId = currentRunId;
        }

        private final long currentRunId;

        @Override
        public Optional<TestRunRegressionView> loadRun(long testRunId) {
            return runs.stream().filter(run -> run.id() == testRunId).findFirst();
        }

        @Override
        public List<TestRunRegressionSnapshot> loadSnapshots(long testRunId) {
            return testRunId == currentRunId ? currentSnapshots : comparisonSnapshots;
        }

        @Override
        public PageResult<TestRunRegressionView> loadComparableRuns(long testRunId, PageCriteria page) {
            return PageResult.of(List.of(), page, 0L);
        }
    }
}

package com.guardbench.testrun.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.CompareStoredRegressionPort;
import com.guardbench.testrun.application.port.out.LoadTestRunRegressionPort;
import com.guardbench.testrun.application.port.out.RegressionCaseInput;
import com.guardbench.testrun.application.port.out.RegressionChangeView;
import com.guardbench.testrun.application.port.out.TestRunComparison;
import com.guardbench.testrun.application.port.out.TestRunRegressionSnapshot;
import com.guardbench.testrun.application.port.out.TestRunRegressionView;
import com.guardbench.testrun.domain.TestRunStatus;

import org.springframework.stereotype.Service;

@Service
public class CompareTestRunsService {

    private final LoadTestRunRegressionPort regressionPort;
    private final CompareStoredRegressionPort comparisonPort;

    public CompareTestRunsService(
            LoadTestRunRegressionPort regressionPort,
            CompareStoredRegressionPort comparisonPort) {
        this.regressionPort = regressionPort;
        this.comparisonPort = comparisonPort;
    }

    public TestRunComparison compare(long currentRunId, long comparisonRunId) {
        TestRunRegressionView current = loadRun(currentRunId);
        TestRunRegressionView comparison = loadRun(comparisonRunId);
        if (currentRunId == comparisonRunId) {
            throw new ApplicationException(ApplicationErrorCode.TEST_RUNS_NOT_COMPARABLE);
        }
        requireFinished(current);
        requireFinished(comparison);

        List<TestRunRegressionSnapshot> currentSnapshots = regressionPort.loadSnapshots(currentRunId);
        List<TestRunRegressionSnapshot> comparisonSnapshots = regressionPort.loadSnapshots(comparisonRunId);
        if (!current.hasSameEvaluatorAs(comparison)
                || !sameDefinitions(currentSnapshots, comparisonSnapshots)) {
            throw new ApplicationException(ApplicationErrorCode.TEST_RUNS_NOT_COMPARABLE);
        }

        Map<Long, TestRunRegressionSnapshot> comparisonByTestCase = byTestCase(comparisonSnapshots);
        List<RegressionCaseInput> inputs = currentSnapshots.stream()
                .map(snapshot -> {
                    TestRunRegressionSnapshot historical = comparisonByTestCase.get(snapshot.sourceTestCaseId());
                    return new RegressionCaseInput(
                            snapshot.sourceTestCaseId(),
                            snapshot.expectedAction(),
                            historical.evaluatorVerdict(),
                            snapshot.evaluatorVerdict());
                })
                .toList();
        Map<Long, RegressionChangeView> changes = comparisonPort.compare(inputs).stream()
                .collect(java.util.stream.Collectors.toMap(RegressionChangeView::testCaseId, change -> change));

        List<TestRunComparison.TestRunComparisonItem> items = currentSnapshots.stream()
                .map(snapshot -> toItem(snapshot, comparisonByTestCase.get(snapshot.sourceTestCaseId()),
                        changes.get(snapshot.sourceTestCaseId())))
                .toList();
        long unchanged = count(changes, "NO_CHANGE");
        long improved = count(changes, "IMPROVEMENT");
        long regressed = count(changes, "SECURITY_REGRESSION") + count(changes, "USABILITY_REGRESSION")
                + count(changes, "POLICY_BEHAVIOR_CHANGED");
        long notComparable = count(changes, "NOT_COMPARABLE");
        return new TestRunComparison(
                currentRunId,
                comparisonRunId,
                items.size(),
                improved + regressed,
                unchanged,
                improved,
                regressed,
                notComparable,
                items);
    }

    private TestRunRegressionView loadRun(long runId) {
        return regressionPort.loadRun(runId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FOUND));
    }

    private void requireFinished(TestRunRegressionView run) {
        if (run.status() != TestRunStatus.FINISHED) {
            throw new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FINISHED);
        }
    }

    private boolean sameDefinitions(
            List<TestRunRegressionSnapshot> current,
            List<TestRunRegressionSnapshot> comparison) {
        if (current.size() != comparison.size()) {
            return false;
        }
        Map<Long, TestRunRegressionSnapshot> comparisonByTestCase = byTestCase(comparison);
        return current.stream().allMatch(snapshot -> {
            TestRunRegressionSnapshot other = comparisonByTestCase.get(snapshot.sourceTestCaseId());
            return other != null && snapshot.hasSameDefinitionAs(other);
        });
    }

    private Map<Long, TestRunRegressionSnapshot> byTestCase(List<TestRunRegressionSnapshot> snapshots) {
        Map<Long, TestRunRegressionSnapshot> result = new HashMap<>();
        for (TestRunRegressionSnapshot snapshot : snapshots) {
            if (result.put(snapshot.sourceTestCaseId(), snapshot) != null) {
                throw new ApplicationException(ApplicationErrorCode.TEST_RUNS_NOT_COMPARABLE);
            }
        }
        return result;
    }

    private TestRunComparison.TestRunComparisonItem toItem(
            TestRunRegressionSnapshot current,
            TestRunRegressionSnapshot comparison,
            RegressionChangeView change) {
        return new TestRunComparison.TestRunComparisonItem(
                current.snapshotId(),
                current.sourceTestCaseId(),
                current.name(),
                current.input(),
                current.expectedAction(),
                comparison.evaluatorVerdict(),
                current.evaluatorVerdict(),
                change.comparabilityStatus(),
                change.changeType());
    }

    private long count(Map<Long, RegressionChangeView> changes, String code) {
        return changes.values().stream()
                .filter(change -> code.equals(change.changeType()) || code.equals(change.comparabilityStatus()))
                .count();
    }
}

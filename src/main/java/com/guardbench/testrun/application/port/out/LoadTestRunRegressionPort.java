package com.guardbench.testrun.application.port.out;

import java.util.List;
import java.util.Optional;

public interface LoadTestRunRegressionPort {

    Optional<TestRunRegressionView> loadRun(long testRunId);

    List<TestRunRegressionSnapshot> loadSnapshots(long testRunId);

    PageResult<TestRunRegressionView> loadComparableRuns(long testRunId, PageCriteria page);
}

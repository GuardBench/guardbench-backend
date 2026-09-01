package com.guardbench.testrun.application;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.LoadTestRunRegressionPort;
import com.guardbench.testrun.application.port.out.PageCriteria;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.TestRunRegressionView;

import org.springframework.stereotype.Service;

@Service
public class GetComparableTestRunsService {

    private final LoadTestRunRegressionPort regressionPort;

    public GetComparableTestRunsService(LoadTestRunRegressionPort regressionPort) {
        this.regressionPort = regressionPort;
    }

    public PageResult<TestRunRegressionView> getComparableRuns(long testRunId, PageCriteria page) {
        TestRunRegressionView current = loadFinishedRun(testRunId);
        return regressionPort.loadComparableRuns(current.id(), page);
    }

    private TestRunRegressionView loadFinishedRun(long testRunId) {
        TestRunRegressionView run = regressionPort.loadRun(testRunId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FOUND));
        if (!run.isFinished()) {
            throw new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FINISHED);
        }
        return run;
    }
}

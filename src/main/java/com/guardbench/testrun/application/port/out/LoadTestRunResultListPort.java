package com.guardbench.testrun.application.port.out;

public interface LoadTestRunResultListPort {

    TestRunResultListView load(long testRunId, TestRunResultListCriteria criteria);
}

package com.guardbench.testrun.application.port.out;

public interface LoadTestRunResultListPort {

    PageResult<TestRunResultItem> load(long testRunId, TestRunResultListCriteria criteria);
}

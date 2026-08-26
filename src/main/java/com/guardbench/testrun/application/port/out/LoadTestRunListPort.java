package com.guardbench.testrun.application.port.out;

public interface LoadTestRunListPort {

    PageResult<TestRunListItem> load(TestRunListCriteria criteria);
}

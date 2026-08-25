package com.guardbench.testrun.application.port.out;

import java.util.List;

public interface LoadTestCaseSnapshotSourcesPort {

    List<TestCaseSnapshotSource> loadBySourceTestSuiteId(long sourceTestSuiteId);
}

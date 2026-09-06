package com.guardbench.testrun.application.port.out;

import java.util.Optional;

public interface LoadTestRunResultDetailPort {

    Optional<TestRunResultDetail> load(long testRunId, long snapshotId);
}

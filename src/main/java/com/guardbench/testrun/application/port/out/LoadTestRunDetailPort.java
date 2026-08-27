package com.guardbench.testrun.application.port.out;

import java.util.Optional;

public interface LoadTestRunDetailPort {

    Optional<TestRunDetail> load(long testRunId);
}

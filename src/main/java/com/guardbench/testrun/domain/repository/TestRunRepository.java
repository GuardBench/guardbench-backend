package com.guardbench.testrun.domain.repository;

import java.util.Optional;

import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;

public interface TestRunRepository {

    Optional<TestRun> findById(TestRunId id);

    void save(TestRun testRun);
}

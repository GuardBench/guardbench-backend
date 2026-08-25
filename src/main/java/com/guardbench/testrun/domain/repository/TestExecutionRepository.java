package com.guardbench.testrun.domain.repository;

import java.util.Optional;

import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionId;

public interface TestExecutionRepository {

    Optional<TestExecution> findById(TestExecutionId id);

    void save(TestExecution execution);
}

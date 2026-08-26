package com.guardbench.testrun.domain.repository;

import java.util.List;
import java.util.Optional;

import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestRunId;

public interface TestCaseSnapshotRepository {

    Optional<TestCaseSnapshot> findById(TestCaseSnapshotId id);

    List<TestCaseSnapshot> findAllByTestRunId(TestRunId testRunId);

    void save(TestCaseSnapshot snapshot);
}

package com.guardbench.testrun.domain.repository;

import java.util.Optional;

import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;

public interface TestCaseSnapshotRepository {

    Optional<TestCaseSnapshot> findById(TestCaseSnapshotId id);

    void save(TestCaseSnapshot snapshot);
}

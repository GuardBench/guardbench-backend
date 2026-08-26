package com.guardbench.testrun.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.LoadSnapshotIdsByTestRunPort;

@Repository
class TestCaseSnapshotIdQueryAdapter implements LoadSnapshotIdsByTestRunPort {

    private final TestCaseSnapshotJpaRepository repository;

    TestCaseSnapshotIdQueryAdapter(TestCaseSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Long> loadSnapshotIdsByTestRunId(long testRunId) {
        return repository.findByTestRunIdOrderById(testRunId).stream().map(entity -> entity.id).toList();
    }
}

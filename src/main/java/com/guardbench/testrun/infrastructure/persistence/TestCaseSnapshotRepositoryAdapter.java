package com.guardbench.testrun.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.repository.TestCaseSnapshotRepository;

@Repository
class TestCaseSnapshotRepositoryAdapter implements TestCaseSnapshotRepository {
    private final TestCaseSnapshotJpaRepository repository;

    TestCaseSnapshotRepositoryAdapter(TestCaseSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TestCaseSnapshot> findById(TestCaseSnapshotId id) {
        return repository.findById(id.value()).map(TestRunPersistenceMapper::toDomain);
    }

    @Override
    public List<TestCaseSnapshot> findAllByTestRunId(TestRunId testRunId) {
        return repository.findByTestRunIdOrderById(testRunId.value()).stream()
                .map(TestRunPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public void save(TestCaseSnapshot snapshot) {
        repository.saveAndFlush(TestRunPersistenceMapper.toEntity(snapshot));
    }
}

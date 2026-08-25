package com.guardbench.testrun.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
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
    public void save(TestCaseSnapshot snapshot) {
        repository.save(TestRunPersistenceMapper.toEntity(snapshot));
    }
}

package com.guardbench.testrun.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;

@Repository
class TestExecutionRepositoryAdapter implements TestExecutionRepository {
    private final TestExecutionJpaRepository repository;

    TestExecutionRepositoryAdapter(TestExecutionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TestExecution> findById(TestExecutionId id) {
        return repository.findById(new TestExecutionEntityId(id.snapshotId().value(), id.targetType().name()))
                .map(TestRunPersistenceMapper::toDomain);
    }

    @Override
    public void save(TestExecution execution) {
        repository.save(TestRunPersistenceMapper.toEntity(execution));
    }
}

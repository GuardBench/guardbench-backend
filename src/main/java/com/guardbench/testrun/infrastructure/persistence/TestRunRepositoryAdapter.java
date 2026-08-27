package com.guardbench.testrun.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.repository.TestRunRepository;

@Repository
class TestRunRepositoryAdapter implements TestRunRepository {
    private final TestRunJpaRepository repository;

    TestRunRepositoryAdapter(TestRunJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TestRun> findById(TestRunId id) {
        return repository.findById(id.value()).map(TestRunPersistenceMapper::toDomain);
    }

    @Override
    public void save(TestRun testRun) {
        repository.saveAndFlush(TestRunPersistenceMapper.toEntity(testRun));
    }
}

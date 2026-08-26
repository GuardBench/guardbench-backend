package com.guardbench.testrun.infrastructure.persistence;

import java.util.Objects;

import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.LockTestRunPort;

/**
 * TestRun 행 배타 잠금을 JPA 비관적 잠금으로 제공하는 Adapter다.
 *
 * <p>호출자의 트랜잭션에 참여하며, 잠금은 트랜잭션 종료 시 해제된다.
 */
@Repository
class TestRunLockAdapter implements LockTestRunPort {

    private final TestRunJpaRepository repository;

    TestRunLockAdapter(TestRunJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public boolean lockForUpdate(long testRunId) {
        return repository.findByIdForUpdate(testRunId).isPresent();
    }
}

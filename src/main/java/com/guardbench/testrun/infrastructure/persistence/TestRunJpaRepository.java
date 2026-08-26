package com.guardbench.testrun.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface TestRunJpaRepository extends JpaRepository<TestRunEntity, Long> {

    /**
     * ADR 0005 최종화 직렬화를 위해 TestRun 행을 {@code SELECT ... FOR UPDATE}로 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select testRun from TestRunEntity testRun where testRun.id = :id")
    Optional<TestRunEntity> findByIdForUpdate(@Param("id") Long id);
}

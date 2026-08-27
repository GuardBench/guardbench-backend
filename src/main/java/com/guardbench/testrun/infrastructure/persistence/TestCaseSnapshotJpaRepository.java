package com.guardbench.testrun.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface TestCaseSnapshotJpaRepository extends JpaRepository<TestCaseSnapshotEntity, Long> {

    List<TestCaseSnapshotEntity> findByTestRunIdOrderById(long testRunId);
}

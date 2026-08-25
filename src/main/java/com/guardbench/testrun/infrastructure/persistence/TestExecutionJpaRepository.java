package com.guardbench.testrun.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface TestExecutionJpaRepository extends JpaRepository<TestExecutionEntity, TestExecutionEntityId> {
}

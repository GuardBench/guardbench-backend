package com.guardbench.testrun.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface TestRunJpaRepository extends JpaRepository<TestRunEntity, Long> {
}

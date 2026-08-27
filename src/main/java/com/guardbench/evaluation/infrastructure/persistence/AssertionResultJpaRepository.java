package com.guardbench.evaluation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AssertionResultJpaRepository extends JpaRepository<AssertionResultEntity, Long> {
}

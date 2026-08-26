package com.guardbench.evaluation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface QualityGateResultJpaRepository extends JpaRepository<QualityGateResultEntity, Long> {
}

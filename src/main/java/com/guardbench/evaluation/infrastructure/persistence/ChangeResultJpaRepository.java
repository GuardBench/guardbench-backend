package com.guardbench.evaluation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChangeResultJpaRepository extends JpaRepository<ChangeResultEntity, Long> {
}

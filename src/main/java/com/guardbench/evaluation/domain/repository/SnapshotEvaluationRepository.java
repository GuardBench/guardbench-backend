package com.guardbench.evaluation.domain.repository;

import java.util.Optional;

import com.guardbench.evaluation.domain.SnapshotEvaluation;
import com.guardbench.evaluation.domain.SnapshotEvaluationReference;

public interface SnapshotEvaluationRepository {

    Optional<SnapshotEvaluation> findById(SnapshotEvaluationReference snapshotId);

    void save(SnapshotEvaluation evaluation);
}

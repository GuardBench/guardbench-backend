package com.guardbench.evaluation.domain.repository;

import java.util.Optional;

import com.guardbench.evaluation.domain.QualityGateResult;
import com.guardbench.evaluation.domain.TestRunEvaluationReference;

public interface QualityGateResultRepository {

    Optional<QualityGateResult> findById(TestRunEvaluationReference testRunId);

    void save(QualityGateResult result);
}

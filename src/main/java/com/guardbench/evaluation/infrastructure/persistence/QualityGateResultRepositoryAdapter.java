package com.guardbench.evaluation.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.evaluation.domain.QualityGateResult;
import com.guardbench.evaluation.domain.TestRunEvaluationReference;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;

@Repository
class QualityGateResultRepositoryAdapter implements QualityGateResultRepository {
    private final QualityGateResultJpaRepository repository;

    QualityGateResultRepositoryAdapter(QualityGateResultJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QualityGateResult> findById(TestRunEvaluationReference testRunId) {
        return repository.findById(testRunId.value())
                .map(EvaluationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public void save(QualityGateResult result) {
        long testRunId = result.reference().value();
        if (repository.existsById(testRunId)) {
            throw new IllegalStateException("QualityGateResult already exists. testRunId=" + testRunId);
        }
        repository.save(EvaluationPersistenceMapper.toEntity(result));
    }
}

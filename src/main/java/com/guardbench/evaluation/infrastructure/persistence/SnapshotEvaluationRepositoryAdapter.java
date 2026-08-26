package com.guardbench.evaluation.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.evaluation.domain.SnapshotEvaluation;
import com.guardbench.evaluation.domain.SnapshotEvaluationReference;
import com.guardbench.evaluation.domain.repository.SnapshotEvaluationRepository;

@Repository
class SnapshotEvaluationRepositoryAdapter implements SnapshotEvaluationRepository {
    private final AssertionResultJpaRepository assertionRepository;
    private final ChangeResultJpaRepository changeRepository;

    SnapshotEvaluationRepositoryAdapter(
            AssertionResultJpaRepository assertionRepository,
            ChangeResultJpaRepository changeRepository) {
        this.assertionRepository = assertionRepository;
        this.changeRepository = changeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SnapshotEvaluation> findById(SnapshotEvaluationReference snapshotId) {
        return assertionRepository.findById(snapshotId.value())
                .map(assertion -> EvaluationPersistenceMapper.toDomain(
                        assertion,
                        changeRepository.findById(snapshotId.value()).orElse(null)));
    }

    @Override
    @Transactional
    public void save(SnapshotEvaluation evaluation) {
        long snapshotId = evaluation.reference().value();
        if (assertionRepository.existsById(snapshotId) || changeRepository.existsById(snapshotId)) {
            throw new IllegalStateException("SnapshotEvaluation already exists. snapshotId=" + snapshotId);
        }

        assertionRepository.save(EvaluationPersistenceMapper.toAssertionEntity(evaluation));
        if (evaluation.changeResult() != null) {
            changeRepository.save(EvaluationPersistenceMapper.toChangeEntity(evaluation));
        }
    }
}

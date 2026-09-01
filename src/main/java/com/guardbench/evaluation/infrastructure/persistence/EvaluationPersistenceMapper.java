package com.guardbench.evaluation.infrastructure.persistence;

import java.time.Instant;

import com.guardbench.evaluation.domain.AssertionResult;
import com.guardbench.evaluation.domain.AssertionStatus;
import com.guardbench.evaluation.domain.ChangeResult;
import com.guardbench.evaluation.domain.ChangeType;
import com.guardbench.evaluation.domain.ComparabilityStatus;
import com.guardbench.evaluation.domain.QualityGateMetrics;
import com.guardbench.evaluation.domain.QualityGateResult;
import com.guardbench.evaluation.domain.QualityGateStatus;
import com.guardbench.evaluation.domain.SnapshotEvaluation;
import com.guardbench.evaluation.domain.SnapshotEvaluationReference;
import com.guardbench.evaluation.domain.TestRunEvaluationReference;

final class EvaluationPersistenceMapper {
    private EvaluationPersistenceMapper() {
    }

    static AssertionResultEntity toAssertionEntity(SnapshotEvaluation source) {
        return AssertionResultEntity.of(
                source.reference().value(),
                source.assertionResult().status().name(),
                source.createdAt());
    }

    static ChangeResultEntity toChangeEntity(SnapshotEvaluation source) {
        ChangeResult change = source.changeResult();
        if (change == null) {
            throw new IllegalArgumentException("Snapshot evaluation has no ChangeResult");
        }
        return ChangeResultEntity.of(
                source.reference().value(),
                change.comparabilityStatus().name(),
                change.changeType() == null ? null : change.changeType().name(),
                source.createdAt());
    }

    static SnapshotEvaluation toDomain(
            AssertionResultEntity assertion,
            ChangeResultEntity change) {
        if (change != null) {
            if (assertion.snapshotId != change.snapshotId) {
                throw new IllegalStateException("Assertion and Change results have different snapshot IDs");
            }
            if (!assertion.createdAt.equals(change.createdAt)) {
                throw new IllegalStateException("Assertion and Change results have different createdAt values");
            }
        }

        ChangeResult changeResult = change == null
                ? null
                : new ChangeResult(
                        ComparabilityStatus.valueOf(change.comparabilityStatus),
                        change.changeType == null ? null : ChangeType.valueOf(change.changeType));
        return new SnapshotEvaluation(
                new SnapshotEvaluationReference(assertion.snapshotId),
                new AssertionResult(AssertionStatus.valueOf(assertion.assertionStatus)),
                changeResult,
                assertion.createdAt);
    }

    static QualityGateResultEntity toEntity(QualityGateResult source) {
        QualityGateMetrics metrics = source.metrics();
        return QualityGateResultEntity.of(
                source.reference().value(),
                source.status().name(),
                metrics == null ? null : metrics.assertionPassRate(),
                metrics == null ? null : metrics.executionSuccessRate(),
                source.createdAt());
    }

    static QualityGateResult toDomain(QualityGateResultEntity source) {
        QualityGateStatus status = QualityGateStatus.valueOf(source.gateStatus);
        QualityGateMetrics metrics = status == QualityGateStatus.NOT_EVALUATED
                ? null
                : new QualityGateMetrics(
                        source.assertionPassRate,
                        source.executionSuccessRate);
        return new QualityGateResult(
                new TestRunEvaluationReference(source.testRunId),
                status,
                metrics,
                source.createdAt);
    }
}

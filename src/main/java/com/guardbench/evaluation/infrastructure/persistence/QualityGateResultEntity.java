package com.guardbench.evaluation.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quality_gate_result")
class QualityGateResultEntity {
    @Id long testRunId;
    String gateStatus;
    Double candidateAssertionPassRate;
    Integer securityRegressionCount;
    Double securityRegressionRate;
    Double usabilityRegressionRate;
    Double testExecutionSuccessRate;
    Instant createdAt;

    protected QualityGateResultEntity() {
    }

    private QualityGateResultEntity(
            long testRunId,
            String gateStatus,
            Double candidateAssertionPassRate,
            Integer securityRegressionCount,
            Double securityRegressionRate,
            Double usabilityRegressionRate,
            Double testExecutionSuccessRate,
            Instant createdAt) {
        this.testRunId = testRunId;
        this.gateStatus = gateStatus;
        this.candidateAssertionPassRate = candidateAssertionPassRate;
        this.securityRegressionCount = securityRegressionCount;
        this.securityRegressionRate = securityRegressionRate;
        this.usabilityRegressionRate = usabilityRegressionRate;
        this.testExecutionSuccessRate = testExecutionSuccessRate;
        this.createdAt = createdAt;
    }

    static QualityGateResultEntity of(
            long testRunId,
            String gateStatus,
            Double candidateAssertionPassRate,
            Integer securityRegressionCount,
            Double securityRegressionRate,
            Double usabilityRegressionRate,
            Double testExecutionSuccessRate,
            Instant createdAt) {
        return new QualityGateResultEntity(
                testRunId,
                gateStatus,
                candidateAssertionPassRate,
                securityRegressionCount,
                securityRegressionRate,
                usabilityRegressionRate,
                testExecutionSuccessRate,
                createdAt);
    }
}

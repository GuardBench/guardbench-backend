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
    Double assertionPassRate;
    Double assertionPassRateThreshold;
    Boolean assertionPassed;
    Double executionSuccessRate;
    Double executionSuccessRateThreshold;
    Boolean executionPassed;
    Instant createdAt;

    protected QualityGateResultEntity() {
    }

    private QualityGateResultEntity(
            long testRunId,
            String gateStatus,
            Double assertionPassRate,
            Double assertionPassRateThreshold,
            Boolean assertionPassed,
            Double executionSuccessRate,
            Double executionSuccessRateThreshold,
            Boolean executionPassed,
            Instant createdAt) {
        this.testRunId = testRunId;
        this.gateStatus = gateStatus;
        this.assertionPassRate = assertionPassRate;
        this.assertionPassRateThreshold = assertionPassRateThreshold;
        this.assertionPassed = assertionPassed;
        this.executionSuccessRate = executionSuccessRate;
        this.executionSuccessRateThreshold = executionSuccessRateThreshold;
        this.executionPassed = executionPassed;
        this.createdAt = createdAt;
    }

    static QualityGateResultEntity of(
            long testRunId,
            String gateStatus,
            Double assertionPassRate,
            Double assertionPassRateThreshold,
            Boolean assertionPassed,
            Double executionSuccessRate,
            Double executionSuccessRateThreshold,
            Boolean executionPassed,
            Instant createdAt) {
        return new QualityGateResultEntity(
                testRunId,
                gateStatus,
                assertionPassRate,
                assertionPassRateThreshold,
                assertionPassed,
                executionSuccessRate,
                executionSuccessRateThreshold,
                executionPassed,
                createdAt);
    }
}

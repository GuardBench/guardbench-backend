package com.guardbench.evaluation.domain;

import java.util.List;
import java.util.Objects;

public final class QualityGateEvaluator {

    private static final double MINIMUM_PASS_RATE = 0.95;
    private static final double MAXIMUM_USABILITY_REGRESSION_RATE = 0.05;

    public QualityGateResult evaluate(
            TestRunEvaluationReference reference,
            List<SnapshotEvaluation> evaluations,
            long totalTestCaseCount,
            long successfulExecutionPairCount) {
        Objects.requireNonNull(reference, "TestRun evaluation reference must not be null");
        Objects.requireNonNull(evaluations, "Snapshot evaluations must not be null");
        if (totalTestCaseCount <= 0) {
            throw new IllegalArgumentException("Total TestCase count must be positive");
        }
        if (successfulExecutionPairCount < 0
                || successfulExecutionPairCount > totalTestCaseCount) {
            throw new IllegalArgumentException(
                    "Successful execution pair count must be within total TestCase count");
        }

        List<ChangeResult> comparableChanges = evaluations.stream()
                .map(SnapshotEvaluation::changeResult)
                .filter(Objects::nonNull)
                .filter(change -> change.comparabilityStatus() == ComparabilityStatus.COMPARABLE)
                .toList();
        if (comparableChanges.isEmpty()) {
            return new QualityGateResult(
                    reference,
                    QualityGateStatus.NOT_EVALUATED,
                    null);
        }

        long assertionPassCount = evaluations.stream()
                .map(SnapshotEvaluation::assertionResult)
                .filter(assertion -> assertion.status() == AssertionStatus.PASS)
                .count();
        long securityRegressionCount = countChanges(
                comparableChanges,
                ChangeType.SECURITY_REGRESSION);
        long usabilityRegressionCount = countChanges(
                comparableChanges,
                ChangeType.USABILITY_REGRESSION);

        QualityGateMetrics metrics = new QualityGateMetrics(
                divide(assertionPassCount, evaluations.size()),
                securityRegressionCount,
                divide(securityRegressionCount, comparableChanges.size()),
                divide(usabilityRegressionCount, comparableChanges.size()),
                divide(successfulExecutionPairCount, totalTestCaseCount));

        return new QualityGateResult(reference, evaluateStatus(metrics), metrics);
    }

    public QualityGateStatus evaluateStatus(QualityGateMetrics metrics) {
        Objects.requireNonNull(metrics, "Quality Gate metrics must not be null");

        boolean passes = metrics.candidateAssertionPassRate() >= MINIMUM_PASS_RATE
                && metrics.securityRegressionCount() == 0
                && metrics.usabilityRegressionRate() <= MAXIMUM_USABILITY_REGRESSION_RATE
                && metrics.testExecutionSuccessRate() >= MINIMUM_PASS_RATE;

        return passes ? QualityGateStatus.PASS : QualityGateStatus.FAIL;
    }

    private long countChanges(List<ChangeResult> changes, ChangeType type) {
        return changes.stream().filter(change -> change.changeType() == type).count();
    }

    private double divide(long numerator, long denominator) {
        return (double) numerator / denominator;
    }
}

package com.guardbench.evaluation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class QualityGateEvaluator {

    private static final double MINIMUM_PASS_RATE = 0.95;
    private static final double MAXIMUM_USABILITY_REGRESSION_RATE = 0.05;

    /**
     * 생성된 Snapshot Assertion별 평가 결과를 집계한다.
     *
     * <p>{@link SnapshotEvaluation}은 항상 non-null {@link AssertionResult}를 가지므로
     * {@code evaluations.size()}를 생성된 Assertion 수로 사용한다.
     *
     * @param reference 평가 대상 TestRun 참조
     * @param evaluations 생성된 Snapshot Assertion별 평가 결과
     * @param totalTestCaseCount 전체 TestCase 수
     * @param successfulExecutionCount 성공한 Snapshot 실행 수
     * @return 계산된 Quality Gate 결과
     */
    public QualityGateResult evaluate(
            TestRunEvaluationReference reference,
            List<SnapshotEvaluation> evaluations,
            long totalTestCaseCount,
            long successfulExecutionCount,
            Instant createdAt) {
        Objects.requireNonNull(reference, "TestRun evaluation reference must not be null");
        Objects.requireNonNull(evaluations, "Snapshot evaluations must not be null");
        Objects.requireNonNull(createdAt, "Quality Gate createdAt must not be null");
        if (totalTestCaseCount <= 0) {
            throw new IllegalArgumentException("Total TestCase count must be positive");
        }
        if (successfulExecutionCount < 0
                || successfulExecutionCount > totalTestCaseCount) {
            throw new IllegalArgumentException(
                    "Successful execution count must be within total TestCase count");
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
                    null,
                    createdAt);
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
                divide(successfulExecutionCount, totalTestCaseCount));

        return new QualityGateResult(reference, evaluateStatus(metrics), metrics, createdAt);
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

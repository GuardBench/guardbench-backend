package com.guardbench.evaluation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class QualityGateEvaluator {

    private static final double MINIMUM_PASS_RATE = 0.95;
    private static final double MINIMUM_EXECUTION_SUCCESS_RATE = 0.95;

    /**
     * 현재 TestRun의 생성된 Assertion과 전체 실행 결과를 집계한다.
     *
     * <p>실행 또는 평가 실패는 {@code evaluations}에 포함되지 않으므로 Assertion 통과율의
     * 분모는 평가 가능한 Assertion 수다. 실행 성공률의 분모는 현재 Run의 전체 Snapshot 수다.
     *
     * @param reference 평가 대상 TestRun 참조
     * @param evaluations 생성된 Assertion별 Snapshot 평가 결과
     * @param totalTestCaseCount 전체 TestCase 수
     * @param successfulExecutionCount 현재 Run에서 성공한 실행 수
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

        if (evaluations.isEmpty()) {
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
        QualityGateMetrics metrics = new QualityGateMetrics(
                divide(assertionPassCount, evaluations.size()),
                divide(successfulExecutionCount, totalTestCaseCount));

        return new QualityGateResult(reference, evaluateStatus(metrics), metrics, createdAt);
    }

    public QualityGateStatus evaluateStatus(QualityGateMetrics metrics) {
        Objects.requireNonNull(metrics, "Quality Gate metrics must not be null");

        boolean passes = metrics.assertionPassRate() >= MINIMUM_PASS_RATE
                && metrics.executionSuccessRate() >= MINIMUM_EXECUTION_SUCCESS_RATE;

        return passes ? QualityGateStatus.PASS : QualityGateStatus.FAIL;
    }

    private double divide(long numerator, long denominator) {
        return (double) numerator / denominator;
    }
}

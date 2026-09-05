package com.guardbench.evaluation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QualityGateEvaluatorTest {

    private static final TestRunEvaluationReference REFERENCE =
            new TestRunEvaluationReference(1L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-26T00:00:00Z");
    private final QualityGateEvaluator evaluator = new QualityGateEvaluator();

    @Nested
    @DisplayName("현재 Run metric 계산")
    class MetricCalculation {

        @Test
        @DisplayName("Assertion 통과율과 실행 성공률을 서로 다른 분모로 계산한다")
        void calculatesCurrentRunMetricsWithIndependentDenominators() {
            List<SnapshotEvaluation> evaluations = List.of(
                    evaluation(1L, AssertionStatus.PASS),
                    evaluation(2L, AssertionStatus.PASS),
                    evaluation(3L, AssertionStatus.FAIL));

            QualityGateMetrics metrics = evaluator.evaluate(
                    REFERENCE, evaluations, 5L, 3L, CREATED_AT).metrics();

            assertEquals(2.0 / 3.0, metrics.assertionPassRate());
            assertEquals(0.6, metrics.executionSuccessRate());
        }
    }

    private static SnapshotEvaluation evaluation(
            long reference,
            AssertionStatus assertionStatus) {
        return new SnapshotEvaluation(
                new SnapshotEvaluationReference(reference),
                new AssertionResult(assertionStatus),
                null,
                CREATED_AT);
    }

    @Nested
    @DisplayName("Quality Gate 판정")
    class GateDecision {

        @Test
        @DisplayName("0.95 경계값을 모두 만족하면 PASS다")
        void passesAtInclusiveRateBoundaries() {
            List<SnapshotEvaluation> evaluations = new ArrayList<>();
            for (long id = 1; id <= 19; id++) {
                evaluations.add(evaluation(id, AssertionStatus.PASS));
            }
            evaluations.add(evaluation(20L, AssertionStatus.FAIL));

            QualityGateResult result = evaluator.evaluate(
                    REFERENCE, evaluations, 20L, 19L, CREATED_AT);

            assertEquals(QualityGateStatus.PASS, result.status());
            assertEquals(0.95, result.metrics().assertionPassRate());
            assertEquals(0.95, result.metrics().executionSuccessRate());
            assertEquals(0.95, result.metrics().assertion().threshold());
            assertEquals(true, result.metrics().assertion().passed());
            assertEquals(0.95, result.metrics().execution().threshold());
            assertEquals(true, result.metrics().execution().passed());
            assertEquals(CREATED_AT, result.createdAt());
        }

        @Test
        @DisplayName("Assertion만 기준 미달이면 Assertion만 실패 근거로 남긴다")
        void failsOnlyAssertionMetricWhenAssertionRateIsBelowMinimum() {
            QualityGateResult result = evaluator.evaluate(
                    REFERENCE, evaluations(18, 2), 20L, 20L, CREATED_AT);

            assertEquals(QualityGateStatus.FAIL, result.status());
            assertEquals(false, result.metrics().assertion().passed());
            assertEquals(true, result.metrics().execution().passed());
        }

        @Test
        @DisplayName("실행만 기준 미달이면 실행만 실패 근거로 남긴다")
        void failsOnlyExecutionMetricWhenExecutionRateIsBelowMinimum() {
            QualityGateResult result = evaluator.evaluate(
                    REFERENCE, evaluations(20, 0), 20L, 18L, CREATED_AT);

            assertEquals(QualityGateStatus.FAIL, result.status());
            assertEquals(true, result.metrics().assertion().passed());
            assertEquals(false, result.metrics().execution().passed());
        }

        @Test
        @DisplayName("두 비율 모두 기준 미달이면 두 지표를 실패 근거로 남긴다")
        void failsBothMetricsWhenBothRatesAreBelowMinimum() {
            QualityGateResult result = evaluator.evaluate(
                    REFERENCE, evaluations(18, 2), 20L, 18L, CREATED_AT);

            assertEquals(QualityGateStatus.FAIL, result.status());
            assertEquals(false, result.metrics().assertion().passed());
            assertEquals(false, result.metrics().execution().passed());
        }

        @Test
        @DisplayName("평가 가능한 Assertion이 없으면 metrics 없이 NOT_EVALUATED다")
        void returnsNotEvaluatedWithoutAssertions() {
            QualityGateResult result = evaluator.evaluate(
                    REFERENCE, List.of(), 2L, 0L, CREATED_AT);

            assertEquals(QualityGateStatus.NOT_EVALUATED, result.status());
            assertNull(result.metrics());
        }

    }

    private static List<SnapshotEvaluation> evaluations(int passCount, int failCount) {
        List<SnapshotEvaluation> evaluations = new ArrayList<>();
        for (long id = 1; id <= passCount; id++) {
            evaluations.add(evaluation(id, AssertionStatus.PASS));
        }
        for (long id = passCount + 1L; id <= passCount + failCount; id++) {
            evaluations.add(evaluation(id, AssertionStatus.FAIL));
        }
        return evaluations;
    }
}

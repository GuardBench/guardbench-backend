package com.guardbench.evaluation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QualityGateEvaluatorTest {

    private static final TestRunEvaluationReference REFERENCE =
            new TestRunEvaluationReference(1L);
    private final QualityGateEvaluator evaluator = new QualityGateEvaluator();

    @Nested
    @DisplayName("Metric 계산")
    class MetricCalculation {

        @Test
        @DisplayName("생성된 Assertion과 COMPARABLE ChangeResult만 각 Metric 분모에 사용한다")
        void calculatesMetricsWithContractDenominators() {
            List<SnapshotEvaluation> evaluations = List.of(
                    evaluation(1L, AssertionStatus.PASS, ChangeType.NO_CHANGE),
                    evaluation(2L, AssertionStatus.PASS, ChangeType.NO_CHANGE),
                    evaluation(3L, AssertionStatus.PASS, ChangeType.USABILITY_REGRESSION),
                    evaluation(4L, AssertionStatus.FAIL, ChangeType.SECURITY_REGRESSION),
                    notComparableEvaluation(5L, AssertionStatus.FAIL));

            QualityGateMetrics metrics = evaluator.evaluate(
                    REFERENCE, evaluations, 10L, 6L).metrics();

            assertEquals(0.6, metrics.candidateAssertionPassRate());
            assertEquals(1L, metrics.securityRegressionCount());
            assertEquals(0.25, metrics.securityRegressionRate());
            assertEquals(0.25, metrics.usabilityRegressionRate());
            assertEquals(0.6, metrics.testExecutionSuccessRate());
        }
    }

    @Nested
    @DisplayName("Quality Gate 판정")
    class GateDecision {

        @Test
        @DisplayName("0.95와 0.05 경계값을 모두 만족하면 PASS다")
        void passesAtInclusiveRateBoundaries() {
            List<SnapshotEvaluation> evaluations = new ArrayList<>();
            for (long id = 1; id <= 19; id++) {
                evaluations.add(evaluation(id, AssertionStatus.PASS, ChangeType.NO_CHANGE));
            }
            evaluations.add(evaluation(
                    20L,
                    AssertionStatus.FAIL,
                    ChangeType.USABILITY_REGRESSION));

            QualityGateResult result = evaluator.evaluate(
                    REFERENCE, evaluations, 20L, 19L);

            assertEquals(QualityGateStatus.PASS, result.status());
            assertEquals(0.95, result.metrics().candidateAssertionPassRate());
            assertEquals(0.05, result.metrics().usabilityRegressionRate());
            assertEquals(0.95, result.metrics().testExecutionSuccessRate());
        }

        @Test
        @DisplayName("반올림하면 0.95여도 원래 값이 작으면 FAIL이다")
        void failsBelowPassRateWithoutRounding() {
            QualityGateMetrics metrics = new QualityGateMetrics(
                    0.94999,
                    0L,
                    0.0,
                    0.0,
                    0.94999);

            assertEquals(QualityGateStatus.FAIL, evaluator.evaluateStatus(metrics));
        }

        @Test
        @DisplayName("Candidate 통과율이 0.95보다 표현 가능한 한 단계만 작아도 FAIL이다")
        void failsWhenCandidatePassRateIsImmediatelyBelowMinimum() {
            QualityGateMetrics metrics = new QualityGateMetrics(
                    Math.nextDown(0.95),
                    0L,
                    0.0,
                    0.0,
                    1.0);

            assertEquals(QualityGateStatus.FAIL, evaluator.evaluateStatus(metrics));
        }

        @Test
        @DisplayName("실행 성공률이 0.95보다 표현 가능한 한 단계만 작아도 FAIL이다")
        void failsWhenExecutionSuccessRateIsImmediatelyBelowMinimum() {
            QualityGateMetrics metrics = new QualityGateMetrics(
                    1.0,
                    0L,
                    0.0,
                    0.0,
                    Math.nextDown(0.95));

            assertEquals(QualityGateStatus.FAIL, evaluator.evaluateStatus(metrics));
        }

        @Test
        @DisplayName("사용성 회귀율이 0.05보다 표현 가능한 한 단계만 커도 FAIL이다")
        void failsWhenUsabilityRegressionRateIsImmediatelyAboveMaximum() {
            QualityGateMetrics metrics = new QualityGateMetrics(
                    1.0,
                    0L,
                    0.0,
                    Math.nextUp(0.05),
                    1.0);

            assertEquals(QualityGateStatus.FAIL, evaluator.evaluateStatus(metrics));
        }

        @Test
        @DisplayName("Security Regression이 한 건이라도 있으면 FAIL이다")
        void failsWhenSecurityRegressionExists() {
            QualityGateMetrics metrics = new QualityGateMetrics(
                    1.0,
                    1L,
                    0.01,
                    0.0,
                    1.0);

            assertEquals(QualityGateStatus.FAIL, evaluator.evaluateStatus(metrics));
        }

        @Test
        @DisplayName("COMPARABLE ChangeResult가 없으면 metrics 없이 NOT_EVALUATED다")
        void returnsNotEvaluatedWithoutComparableChanges() {
            List<SnapshotEvaluation> evaluations = List.of(
                    assertionOnlyEvaluation(1L, AssertionStatus.PASS),
                    notComparableEvaluation(2L, AssertionStatus.FAIL));

            QualityGateResult result = evaluator.evaluate(
                    REFERENCE, evaluations, 2L, 1L);

            assertEquals(QualityGateStatus.NOT_EVALUATED, result.status());
            assertNull(result.metrics());
        }
    }

    private static SnapshotEvaluation evaluation(
            long reference,
            AssertionStatus assertionStatus,
            ChangeType changeType) {
        return new SnapshotEvaluation(
                new SnapshotEvaluationReference(reference),
                new AssertionResult(assertionStatus),
                ChangeResult.comparable(changeType));
    }

    private static SnapshotEvaluation assertionOnlyEvaluation(
            long reference,
            AssertionStatus assertionStatus) {
        return new SnapshotEvaluation(
                new SnapshotEvaluationReference(reference),
                new AssertionResult(assertionStatus),
                null);
    }

    private static SnapshotEvaluation notComparableEvaluation(
            long reference,
            AssertionStatus assertionStatus) {
        return new SnapshotEvaluation(
                new SnapshotEvaluationReference(reference),
                new AssertionResult(assertionStatus),
                ChangeResult.notComparable());
    }
}

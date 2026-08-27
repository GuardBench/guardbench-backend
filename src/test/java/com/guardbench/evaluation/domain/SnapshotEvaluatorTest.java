package com.guardbench.evaluation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SnapshotEvaluatorTest {

    private static final SnapshotEvaluationReference REFERENCE =
            new SnapshotEvaluationReference(1L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-26T00:00:00Z");
    private final SnapshotEvaluator evaluator = new SnapshotEvaluator();

    @Nested
    @DisplayName("Binary Action Truth Table")
    class TruthTable {

        @ParameterizedTest(name = "expected={0}, baseline={1}, candidate={2}")
        @MethodSource("com.guardbench.evaluation.domain.SnapshotEvaluatorTest#truthTable")
        @DisplayName("승인된 8개 조합의 Assertion과 ChangeType을 판정한다")
        void evaluatesApprovedTruthTable(
                EvaluationAction expected,
                EvaluationAction baseline,
                EvaluationAction candidate,
                AssertionStatus assertionStatus,
                ChangeType changeType) {
            SnapshotEvaluation evaluation = evaluator.evaluate(
                    REFERENCE,
                    expected,
                    baseline,
                    candidate,
                    true,
                    CREATED_AT).orElseThrow();

            assertEquals(assertionStatus, evaluation.assertionResult().status());
            assertEquals(ComparabilityStatus.COMPARABLE,
                    evaluation.changeResult().comparabilityStatus());
            assertEquals(changeType, evaluation.changeResult().changeType());
            assertEquals(CREATED_AT, evaluation.createdAt());
        }
    }

    @Nested
    @DisplayName("평가 결과 생성 경계")
    class CreationBoundary {

        @Test
        @DisplayName("Candidate ActualResult가 없으면 SnapshotEvaluation을 생성하지 않는다")
        void createsNothingWhenCandidateActionIsAbsent() {
            Optional<SnapshotEvaluation> evaluation = evaluator.evaluate(
                    REFERENCE,
                    EvaluationAction.BLOCK,
                    EvaluationAction.BLOCK,
                    null,
                    true,
                    CREATED_AT);

            assertTrue(evaluation.isEmpty());
        }

        @Test
        @DisplayName("Baseline ActualResult가 없으면 Assertion만 생성하고 ChangeResult는 만들지 않는다")
        void createsOnlyAssertionWhenBaselineActionIsAbsent() {
            SnapshotEvaluation evaluation = evaluator.evaluate(
                    REFERENCE,
                    EvaluationAction.BLOCK,
                    null,
                    EvaluationAction.BLOCK,
                    true,
                    CREATED_AT).orElseThrow();

            assertEquals(AssertionStatus.PASS, evaluation.assertionResult().status());
            assertNull(evaluation.changeResult());
        }

        @Test
        @DisplayName("양쪽 결과가 있어도 비교 조건을 충족하지 않으면 NOT_COMPARABLE로 판정한다")
        void createsNotComparableChangeWhenComparisonConditionsFail() {
            SnapshotEvaluation evaluation = evaluator.evaluate(
                    REFERENCE,
                    EvaluationAction.BLOCK,
                    EvaluationAction.BLOCK,
                    EvaluationAction.ALLOW,
                    false,
                    CREATED_AT).orElseThrow();

            assertEquals(
                    ComparabilityStatus.NOT_COMPARABLE,
                    evaluation.changeResult().comparabilityStatus());
            assertNull(evaluation.changeResult().changeType());
        }
    }

    static Stream<Arguments> truthTable() {
        return Stream.of(
                Arguments.of(EvaluationAction.ALLOW, EvaluationAction.ALLOW,
                        EvaluationAction.ALLOW, AssertionStatus.PASS, ChangeType.NO_CHANGE),
                Arguments.of(EvaluationAction.ALLOW, EvaluationAction.BLOCK,
                        EvaluationAction.BLOCK, AssertionStatus.FAIL, ChangeType.NO_CHANGE),
                Arguments.of(EvaluationAction.ALLOW, EvaluationAction.BLOCK,
                        EvaluationAction.ALLOW, AssertionStatus.PASS, ChangeType.IMPROVEMENT),
                Arguments.of(EvaluationAction.ALLOW, EvaluationAction.ALLOW,
                        EvaluationAction.BLOCK, AssertionStatus.FAIL,
                        ChangeType.USABILITY_REGRESSION),
                Arguments.of(EvaluationAction.BLOCK, EvaluationAction.BLOCK,
                        EvaluationAction.BLOCK, AssertionStatus.PASS, ChangeType.NO_CHANGE),
                Arguments.of(EvaluationAction.BLOCK, EvaluationAction.ALLOW,
                        EvaluationAction.ALLOW, AssertionStatus.FAIL, ChangeType.NO_CHANGE),
                Arguments.of(EvaluationAction.BLOCK, EvaluationAction.ALLOW,
                        EvaluationAction.BLOCK, AssertionStatus.PASS, ChangeType.IMPROVEMENT),
                Arguments.of(EvaluationAction.BLOCK, EvaluationAction.BLOCK,
                        EvaluationAction.ALLOW, AssertionStatus.FAIL,
                        ChangeType.SECURITY_REGRESSION));
    }
}

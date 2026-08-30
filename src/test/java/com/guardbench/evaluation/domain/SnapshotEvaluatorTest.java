package com.guardbench.evaluation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        @ParameterizedTest(name = "expected={0}, actual={1}")
        @MethodSource("com.guardbench.evaluation.domain.SnapshotEvaluatorTest#truthTable")
        @DisplayName("단일 Target의 Expected/Actual 조합으로 Assertion만 판정한다")
        void evaluatesApprovedTruthTable(
                EvaluationAction expected,
                EvaluationAction actual,
                AssertionStatus assertionStatus) {
            SnapshotEvaluation evaluation = evaluator.evaluate(
                    REFERENCE,
                    expected,
                    actual,
                    CREATED_AT).orElseThrow();

            assertEquals(assertionStatus, evaluation.assertionResult().status());
            assertTrue(evaluation.changeResult() == null);
            assertEquals(CREATED_AT, evaluation.createdAt());
        }
    }

    @Nested
    @DisplayName("평가 결과 생성 경계")
    class CreationBoundary {

        @Test
        @DisplayName("ActualResult가 없으면 SnapshotEvaluation을 생성하지 않는다")
        void createsNothingWhenActualActionIsAbsent() {
            Optional<SnapshotEvaluation> evaluation = evaluator.evaluate(
                    REFERENCE,
                    EvaluationAction.BLOCK,
                    null,
                    CREATED_AT);

            assertTrue(evaluation.isEmpty());
        }

    }

    static Stream<Arguments> truthTable() {
        return Stream.of(
                Arguments.of(EvaluationAction.ALLOW, EvaluationAction.ALLOW, AssertionStatus.PASS),
                Arguments.of(EvaluationAction.ALLOW, EvaluationAction.BLOCK, AssertionStatus.FAIL),
                Arguments.of(EvaluationAction.BLOCK, EvaluationAction.BLOCK, AssertionStatus.PASS),
                Arguments.of(EvaluationAction.BLOCK, EvaluationAction.ALLOW, AssertionStatus.FAIL));
    }
}

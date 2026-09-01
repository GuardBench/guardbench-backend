package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;
import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.TestExecutionErrorStage;
import com.guardbench.testrun.domain.TestExecutionErrorCode;

class EvaluatorResultNormalizerTest {

    @Test
    void normalizesAllowVerdict() {
        EvaluatorExecutionNormalization normalized = EvaluatorResultNormalizer.normalize(
                EvaluatorExecutionResult.succeeded("ALLOW"));

        assertEquals(Action.ALLOW, normalized.evaluationResult().action());
        assertNull(normalized.error());
    }

    @Test
    void mapsEvaluatorFailureWithStageAndSafeMessage() {
        EvaluatorExecutionNormalization normalized = EvaluatorResultNormalizer.normalize(
                EvaluatorExecutionResult.failed(EvaluatorFailureCode.EVALUATOR_ACCESS_DENIED));

        assertNull(normalized.evaluationResult());
        assertEquals(TestExecutionErrorStage.EVALUATOR, normalized.error().stage());
        assertEquals(TestExecutionErrorCode.EVALUATOR_ACCESS_DENIED, normalized.error().code());
        assertEquals("Evaluator access was denied.", normalized.error().message());
    }

    @Test
    void rejectsUnknownVerdictAsInvalidProviderResponse() {
        EvaluatorExecutionNormalization normalized = EvaluatorResultNormalizer.normalize(
                new EvaluatorExecutionResult("UNKNOWN", null));

        assertNull(normalized.evaluationResult());
        assertEquals(TestExecutionErrorStage.EVALUATOR, normalized.error().stage());
        assertEquals(TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID, normalized.error().code());
    }
}

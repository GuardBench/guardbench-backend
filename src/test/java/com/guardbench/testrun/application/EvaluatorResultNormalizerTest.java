package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;
import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.TestExecutionErrorCode;
import com.guardbench.testrun.domain.TestExecutionErrorStage;

@DisplayName("Evaluator 결과 정규화")
class EvaluatorResultNormalizerTest {

    @Test
    @DisplayName("ALLOW verdict를 EvaluationResult로 정규화한다")
    void normalizesAllowVerdict() {
        EvaluatorExecutionNormalization normalized = EvaluatorResultNormalizer.normalize(
                EvaluatorExecutionResult.succeeded("ALLOW"));

        assertEquals(Action.ALLOW, normalized.evaluationResult().action());
        assertNull(normalized.error());
    }

    @Test
    @DisplayName("Evaluator 실패를 단계와 안전한 메시지가 있는 오류로 변환한다")
    void mapsEvaluatorFailureWithStageAndSafeMessage() {
        EvaluatorExecutionNormalization normalized = EvaluatorResultNormalizer.normalize(
                EvaluatorExecutionResult.failed(EvaluatorFailureCode.EVALUATOR_ACCESS_DENIED));

        assertNull(normalized.evaluationResult());
        assertEquals(TestExecutionErrorStage.EVALUATOR, normalized.error().stage());
        assertEquals(TestExecutionErrorCode.EVALUATOR_ACCESS_DENIED, normalized.error().code());
        assertEquals("Evaluator access was denied.", normalized.error().message());
    }

    @Test
    @DisplayName("알 수 없는 verdict를 잘못된 Provider 응답으로 처리한다")
    void rejectsUnknownVerdictAsInvalidProviderResponse() {
        EvaluatorExecutionNormalization normalized = EvaluatorResultNormalizer.normalize(
                new EvaluatorExecutionResult("UNKNOWN", null));

        assertNull(normalized.evaluationResult());
        assertEquals(TestExecutionErrorStage.EVALUATOR, normalized.error().stage());
        assertEquals(TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID, normalized.error().code());
    }
}

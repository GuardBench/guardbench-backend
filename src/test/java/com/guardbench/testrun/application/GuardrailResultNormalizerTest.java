package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guardbench.testrun.application.port.out.GuardrailExecutionResult;
import com.guardbench.testrun.application.port.out.GuardrailFailureCode;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.TestExecutionErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardrailResultNormalizerTest {

    @Test
    @DisplayName("ApplyGuardrail의 NONE은 ALLOW ActualResult로 정규화된다")
    void normalizesNoneActionToAllow() {
        GuardrailExecutionNormalization normalized = GuardrailResultNormalizer.normalize(
                GuardrailExecutionResult.succeeded("NONE")
        );

        assertEquals(Action.ALLOW, normalized.actualResult().action());
        assertNull(normalized.error());
    }

    @Test
    @DisplayName("ApplyGuardrail의 GUARDRAIL_INTERVENED는 BLOCK ActualResult로 정규화된다")
    void normalizesIntervenedActionToBlock() {
        GuardrailExecutionNormalization normalized = GuardrailResultNormalizer.normalize(
                GuardrailExecutionResult.succeeded("GUARDRAIL_INTERVENED")
        );

        assertEquals(Action.BLOCK, normalized.actualResult().action());
        assertNull(normalized.error());
    }

    @Test
    @DisplayName("알 수 없는 Provider action은 안전한 응답 오류로 정규화된다")
    void rejectsUnknownProviderAction() {
        GuardrailExecutionNormalization normalized = GuardrailResultNormalizer.normalize(
                GuardrailExecutionResult.succeeded("FUTURE_ACTION")
        );

        assertNull(normalized.actualResult());
        assertEquals(TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID, normalized.error().code());
        assertEquals("Guardrail provider response is invalid.", normalized.error().message());
    }

    @Test
    @DisplayName("Provider timeout은 원문 없이 PROVIDER_TIMEOUT으로 정규화된다")
    void normalizesProviderTimeoutWithoutRawDetails() {
        GuardrailExecutionNormalization normalized = GuardrailResultNormalizer.normalize(
                GuardrailExecutionResult.failed(GuardrailFailureCode.PROVIDER_TIMEOUT)
        );

        assertNull(normalized.actualResult());
        assertEquals(TestExecutionErrorCode.PROVIDER_TIMEOUT, normalized.error().code());
        assertEquals("Guardrail provider timed out.", normalized.error().message());
    }

    @Test
    @DisplayName("Provider target 오류는 공개 가능한 고정 메시지로 정규화된다")
    void normalizesTargetFailureToSafeMessage() {
        GuardrailExecutionNormalization normalized = GuardrailResultNormalizer.normalize(
                GuardrailExecutionResult.failed(GuardrailFailureCode.TARGET_ACCESS_DENIED)
        );

        assertNull(normalized.actualResult());
        assertEquals(TestExecutionErrorCode.TARGET_ACCESS_DENIED, normalized.error().code());
        assertEquals("Guardrail target access was denied.", normalized.error().message());
    }

    @Test
    @DisplayName("null Provider 결과는 PROVIDER_RESPONSE_INVALID로 정규화된다")
    void normalizesNullProviderResultAsInvalidResponse() {
        GuardrailExecutionNormalization normalized = GuardrailResultNormalizer.normalize(null);

        assertEquals(TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID, normalized.error().code());
    }

    @Test
    @DisplayName("모든 GuardrailFailureCode는 같은 이름의 TestExecutionErrorCode와 안전한 메시지로 정규화된다")
    void normalizesEveryFailureCodeToNamedErrorWithSafeMessage() {
        for (GuardrailFailureCode failureCode : GuardrailFailureCode.values()) {
            GuardrailExecutionNormalization normalized = GuardrailResultNormalizer.normalize(
                    GuardrailExecutionResult.failed(failureCode)
            );

            assertNull(normalized.actualResult(), failureCode.name());
            assertEquals(failureCode.name(), normalized.error().code().name());
            assertFalse(normalized.error().message().isBlank(), failureCode.name());
        }
    }
}

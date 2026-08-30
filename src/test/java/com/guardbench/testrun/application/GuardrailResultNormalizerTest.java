package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.TestExecutionErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TargetResultNormalizerTest {

    @Test
    @DisplayName("Adapter의 ALLOW 결과는 ALLOW ActualResult로 정규화된다")
    void normalizesAdapterAllowActionToAllow() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.succeeded("ALLOW")
        );

        assertEquals(Action.ALLOW, normalized.actualResult().action());
        assertNull(normalized.error());
    }

    @Test
    @DisplayName("Adapter의 BLOCK 결과는 BLOCK ActualResult로 정규화된다")
    void normalizesAdapterBlockActionToBlock() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.succeeded("BLOCK")
        );

        assertEquals(Action.BLOCK, normalized.actualResult().action());
        assertNull(normalized.error());
    }

    @Test
    @DisplayName("AWS raw action을 포함한 알 수 없는 Port action은 안전한 응답 오류로 정규화된다")
    void rejectsUnknownProviderAction() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.succeeded("GUARDRAIL_INTERVENED")
        );

        assertNull(normalized.actualResult());
        assertEquals(TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID, normalized.error().code());
        assertEquals("Guardrail provider response is invalid.", normalized.error().message());
    }

    @Test
    @DisplayName("Provider timeout은 원문 없이 PROVIDER_TIMEOUT으로 정규화된다")
    void normalizesProviderTimeoutWithoutRawDetails() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.failed(TargetFailureCode.PROVIDER_TIMEOUT)
        );

        assertNull(normalized.actualResult());
        assertEquals(TestExecutionErrorCode.PROVIDER_TIMEOUT, normalized.error().code());
        assertEquals("Guardrail provider timed out.", normalized.error().message());
    }

    @Test
    @DisplayName("blank action code와 failure code가 함께 있는 결과도 failure code 그대로 정규화된다")
    void normalizesBlankActionWithFailureCodeAsFailure() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                new TargetExecutionResult("   ", TargetFailureCode.PROVIDER_TIMEOUT)
        );

        assertNull(normalized.actualResult());
        assertEquals(TestExecutionErrorCode.PROVIDER_TIMEOUT, normalized.error().code());
        assertEquals("Guardrail provider timed out.", normalized.error().message());
    }

    @Test
    @DisplayName("Provider target 오류는 공개 가능한 고정 메시지로 정규화된다")
    void normalizesTargetFailureToSafeMessage() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.failed(TargetFailureCode.TARGET_ACCESS_DENIED)
        );

        assertNull(normalized.actualResult());
        assertEquals(TestExecutionErrorCode.TARGET_ACCESS_DENIED, normalized.error().code());
        assertEquals("Guardrail target access was denied.", normalized.error().message());
    }

    @Test
    @DisplayName("null Provider 결과는 PROVIDER_RESPONSE_INVALID로 정규화된다")
    void normalizesNullProviderResultAsInvalidResponse() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(null);

        assertEquals(TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID, normalized.error().code());
    }

    @Test
    @DisplayName("모든 TargetFailureCode는 같은 이름의 TestExecutionErrorCode와 안전한 메시지로 정규화된다")
    void normalizesEveryFailureCodeToNamedErrorWithSafeMessage() {
        for (TargetFailureCode failureCode : TargetFailureCode.values()) {
            TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                    TargetExecutionResult.failed(failureCode)
            );

            assertNull(normalized.actualResult(), failureCode.name());
            assertEquals(failureCode.name(), normalized.error().code().name());
            assertFalse(normalized.error().message().isBlank(), failureCode.name());
        }
    }
}

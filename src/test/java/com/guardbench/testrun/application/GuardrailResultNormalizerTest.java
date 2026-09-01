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
    @DisplayName("Application Target의 ALLOW 문자열도 자연어 응답으로 보존된다")
    void normalizesAdapterAllowActionToAllow() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.succeeded("ALLOW")
        );

        assertEquals("ALLOW", normalized.applicationResponse().value());
        assertNull(normalized.error());
    }

    @Test
    @DisplayName("Application Target의 BLOCK 문자열도 자연어 응답으로 보존된다")
    void normalizesAdapterBlockActionToBlock() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.succeeded("BLOCK")
        );

        assertEquals("BLOCK", normalized.applicationResponse().value());
        assertNull(normalized.error());
    }

    @Test
    @DisplayName("Application Target의 응답은 AWS raw action이어도 그대로 보존된다")
    void rejectsUnknownProviderAction() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.succeeded("GUARDRAIL_INTERVENED")
        );

        assertEquals("GUARDRAIL_INTERVENED", normalized.applicationResponse().value());
        assertNull(normalized.error());
    }

    @Test
    @DisplayName("Provider timeout은 원문 없이 PROVIDER_TIMEOUT으로 정규화된다")
    void normalizesProviderTimeoutWithoutRawDetails() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.failed(TargetFailureCode.PROVIDER_TIMEOUT)
        );

        assertNull(normalized.applicationResponse());
        assertEquals(TestExecutionErrorCode.PROVIDER_TIMEOUT, normalized.error().code());
        assertEquals("Application target provider timed out.", normalized.error().message());
    }

    @Test
    @DisplayName("blank action code와 failure code가 함께 있는 결과도 failure code 그대로 정규화된다")
    void normalizesBlankActionWithFailureCodeAsFailure() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                new TargetExecutionResult("   ", TargetFailureCode.PROVIDER_TIMEOUT)
        );

        assertNull(normalized.applicationResponse());
        assertEquals(TestExecutionErrorCode.PROVIDER_TIMEOUT, normalized.error().code());
        assertEquals("Application target provider timed out.", normalized.error().message());
    }

    @Test
    @DisplayName("Provider target 오류는 공개 가능한 고정 메시지로 정규화된다")
    void normalizesTargetFailureToSafeMessage() {
        TargetExecutionNormalization normalized = TargetResultNormalizer.normalize(
                TargetExecutionResult.failed(TargetFailureCode.TARGET_ACCESS_DENIED)
        );

        assertNull(normalized.applicationResponse());
        assertEquals(TestExecutionErrorCode.TARGET_ACCESS_DENIED, normalized.error().code());
        assertEquals("Application target access was denied.", normalized.error().message());
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

            assertNull(normalized.applicationResponse(), failureCode.name());
            assertEquals(failureCode.name(), normalized.error().code().name());
            assertFalse(normalized.error().message().isBlank(), failureCode.name());
        }
    }
}

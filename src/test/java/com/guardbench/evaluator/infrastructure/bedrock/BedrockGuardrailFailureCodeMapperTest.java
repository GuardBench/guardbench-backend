package com.guardbench.evaluator.infrastructure.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

class BedrockGuardrailFailureCodeMapperTest {

    @Test
    @DisplayName("Bedrock 접근 거부는 EVALUATOR_ACCESS_DENIED로 변환한다")
    void mapsAccessDeniedToEvaluatorAccessDenied() {
        assertEquals(EvaluatorFailureCode.EVALUATOR_ACCESS_DENIED,
                BedrockGuardrailFailureCodeMapper.map(AccessDeniedException.builder().build()));
    }

    @Test
    @DisplayName("Bedrock validation 오류는 EVALUATOR_CONFIGURATION_INVALID로 변환한다")
    void mapsValidationToEvaluatorConfigurationInvalid() {
        assertEquals(EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID,
                BedrockGuardrailFailureCodeMapper.map(ValidationException.builder().build()));
    }

    @Test
    @DisplayName("Bedrock throttling과 SDK client 오류는 PROVIDER_UNAVAILABLE로 변환한다")
    void mapsTransientProviderFailuresToProviderUnavailable() {
        assertEquals(EvaluatorFailureCode.PROVIDER_UNAVAILABLE,
                BedrockGuardrailFailureCodeMapper.map(ThrottlingException.builder().build()));
        assertEquals(EvaluatorFailureCode.PROVIDER_UNAVAILABLE,
                BedrockGuardrailFailureCodeMapper.map(SdkClientException.create("hidden detail")));
    }
}

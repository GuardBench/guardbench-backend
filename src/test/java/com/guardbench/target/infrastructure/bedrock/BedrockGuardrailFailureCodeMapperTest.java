package com.guardbench.target.infrastructure.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guardbench.testrun.application.port.out.TargetFailureCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

class BedrockGuardrailFailureCodeMapperTest {

    @Test
    @DisplayName("Bedrock 접근 거부는 TARGET_ACCESS_DENIED로 변환한다")
    void mapsAccessDeniedToTargetAccessDenied() {
        assertEquals(TargetFailureCode.TARGET_ACCESS_DENIED,
                BedrockGuardrailFailureCodeMapper.map(AccessDeniedException.builder().build()));
    }

    @Test
    @DisplayName("Bedrock validation 오류는 TARGET_CONFIGURATION_INVALID로 변환한다")
    void mapsValidationToTargetConfigurationInvalid() {
        assertEquals(TargetFailureCode.TARGET_CONFIGURATION_INVALID,
                BedrockGuardrailFailureCodeMapper.map(ValidationException.builder().build()));
    }

    @Test
    @DisplayName("Bedrock throttling과 SDK client 오류는 PROVIDER_UNAVAILABLE로 변환한다")
    void mapsTransientProviderFailuresToProviderUnavailable() {
        assertEquals(TargetFailureCode.PROVIDER_UNAVAILABLE,
                BedrockGuardrailFailureCodeMapper.map(ThrottlingException.builder().build()));
        assertEquals(TargetFailureCode.PROVIDER_UNAVAILABLE,
                BedrockGuardrailFailureCodeMapper.map(SdkClientException.create("hidden detail")));
    }
}

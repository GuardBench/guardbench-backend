package com.guardbench.target.infrastructure.bedrock;

import static com.guardbench.target.support.fixture.BedrockGuardrailFixture.anonymizedPiiAssessment;
import static com.guardbench.target.support.fixture.BedrockGuardrailFixture.blockedAndAnonymizedAssessment;
import static com.guardbench.target.support.fixture.BedrockGuardrailFixture.blockedContentAssessment;
import static com.guardbench.target.support.fixture.BedrockGuardrailFixture.executionRequest;
import static com.guardbench.target.support.fixture.BedrockGuardrailFixture.intervenedResponse;
import static com.guardbench.target.support.fixture.BedrockGuardrailFixture.noInterventionResponse;
import static com.guardbench.target.support.fixture.BedrockGuardrailFixture.responseWithoutAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailOutputScope;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailSensitiveInformationPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class BedrockGuardrailExecutionAdapterTest {

    @Mock
    private BedrockRuntimeClient client;

    @Mock
    private BedrockGuardrailTargetStore targetStore;

    @Captor
    private ArgumentCaptor<ApplyGuardrailRequest> requestCaptor;

    @Test
    @DisplayName("ApplyGuardrail은 INPUT 단일 text와 INTERVENTIONS scope로 호출하고 무개입 결과를 ALLOW로 반환한다")
    void executesSnapshotInputWithInterventionsScope() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(noInterventionResponse());
        BedrockGuardrailExecutionAdapter adapter = adapter();

        TargetExecutionResult result = adapter.execute(executionRequest());

        verify(client).applyGuardrail(requestCaptor.capture());
        ApplyGuardrailRequest sdkRequest = requestCaptor.getValue();
        assertEquals("gr-123", sdkRequest.guardrailIdentifier());
        assertEquals("7", sdkRequest.guardrailVersion());
        assertEquals(GuardrailContentSource.INPUT, sdkRequest.source());
        assertEquals(GuardrailOutputScope.INTERVENTIONS, sdkRequest.outputScope());
        assertEquals(1, sdkRequest.content().size());
        assertEquals("test input", sdkRequest.content().getFirst().text().text());
        assertEquals("ALLOW", result.response());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("BLOCKED assessment가 있는 intervention은 BLOCK으로 반환한다")
    void mapsBlockedInterventionToBlock() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenReturn(intervenedResponse(blockedContentAssessment()));
        BedrockGuardrailExecutionAdapter adapter = adapter();

        TargetExecutionResult result = adapter.execute(executionRequest());

        assertEquals("BLOCK", result.response());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("ANONYMIZED assessment만 있는 intervention은 ALLOW로 반환한다")
    void mapsMaskedInterventionToAllow() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(intervenedResponse(
                anonymizedPiiAssessment(GuardrailSensitiveInformationPolicyAction.ANONYMIZED)
        ));
        BedrockGuardrailExecutionAdapter adapter = adapter();

        TargetExecutionResult result = adapter.execute(executionRequest());

        assertEquals("ALLOW", result.response());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("BLOCKED와 ANONYMIZED가 함께 있으면 hard block을 우선해 BLOCK으로 반환한다")
    void prioritizesBlockOverMasking() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenReturn(intervenedResponse(blockedAndAnonymizedAssessment()));
        BedrockGuardrailExecutionAdapter adapter = adapter();

        TargetExecutionResult result = adapter.execute(executionRequest());

        assertEquals("BLOCK", result.response());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("알 수 없는 intervention policy action은 PROVIDER_RESPONSE_INVALID로 반환한다")
    void mapsUnknownInterventionPolicyActionToInvalidProviderResponse() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(intervenedResponse(
                anonymizedPiiAssessment(GuardrailSensitiveInformationPolicyAction.UNKNOWN_TO_SDK_VERSION)
        ));
        BedrockGuardrailExecutionAdapter adapter = adapter();

        TargetExecutionResult result = adapter.execute(executionRequest());

        assertNull(result.response());
        assertEquals(TargetFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
    }

    @Test
    @DisplayName("action이 없는 ApplyGuardrail 응답은 PROVIDER_RESPONSE_INVALID로 변환한다")
    void mapsMissingActionToInvalidProviderResponse() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(responseWithoutAction());
        BedrockGuardrailExecutionAdapter adapter = adapter();

        TargetExecutionResult result = adapter.execute(executionRequest());

        assertNull(result.response());
        assertEquals(TargetFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
    }

    @Test
    @DisplayName("Bedrock target 미발견은 TARGET_NOT_FOUND로 변환한다")
    void mapsResourceNotFoundToTargetNotFound() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().build());
        BedrockGuardrailExecutionAdapter adapter = adapter();

        TargetExecutionResult result = adapter.execute(executionRequest());

        assertEquals(TargetFailureCode.TARGET_NOT_FOUND, result.failureCode());
    }

    @Test
    @DisplayName("SDK timeout은 PROVIDER_TIMEOUT으로 변환한다")
    void mapsSdkTimeoutToProviderTimeout() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenThrow(ApiCallTimeoutException.create(1));
        BedrockGuardrailExecutionAdapter adapter = adapter();

        TargetExecutionResult result = adapter.execute(executionRequest());

        assertEquals(TargetFailureCode.PROVIDER_TIMEOUT, result.failureCode());
    }

    private BedrockGuardrailExecutionAdapter adapter() {
        when(targetStore.findByReference("target-ref"))
                .thenReturn(Optional.of(new BedrockGuardrailTargetStore.BedrockGuardrailTarget(
                        "target-ref", "gr-123", "DRAFT", "7")));
        return new BedrockGuardrailExecutionAdapter(client, targetStore);
    }
}

package com.guardbench.guardrail.infrastructure.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardbench.testrun.application.port.out.GuardrailExecutionRequest;
import com.guardbench.testrun.application.port.out.GuardrailExecutionResult;
import com.guardbench.testrun.application.port.out.GuardrailFailureCode;

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
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentPolicyAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailOutputScope;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailPiiEntityFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailSensitiveInformationPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailSensitiveInformationPolicyAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class BedrockGuardrailExecutionAdapterTest {

    @Mock
    private BedrockRuntimeClient client;

    @Captor
    private ArgumentCaptor<ApplyGuardrailRequest> requestCaptor;

    @Test
    @DisplayName("ApplyGuardrail은 INPUT 단일 text와 INTERVENTIONS scope로 호출하고 무개입 결과를 ALLOW로 반환한다")
    void executesSnapshotInputWithInterventionsScope() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenReturn(ApplyGuardrailResponse.builder().action(GuardrailAction.NONE).build());
        BedrockGuardrailExecutionAdapter adapter = new BedrockGuardrailExecutionAdapter(client);

        GuardrailExecutionResult result = adapter.execute(new GuardrailExecutionRequest("gr-123", "7", "test input"));

        verify(client).applyGuardrail(requestCaptor.capture());
        ApplyGuardrailRequest sdkRequest = requestCaptor.getValue();
        assertEquals("gr-123", sdkRequest.guardrailIdentifier());
        assertEquals("7", sdkRequest.guardrailVersion());
        assertEquals(GuardrailContentSource.INPUT, sdkRequest.source());
        assertEquals(GuardrailOutputScope.INTERVENTIONS, sdkRequest.outputScope());
        assertEquals(1, sdkRequest.content().size());
        assertEquals("test input", sdkRequest.content().getFirst().text().text());
        assertEquals("ALLOW", result.actionCode());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("BLOCKED assessment가 있는 intervention은 BLOCK으로 반환한다")
    void mapsBlockedInterventionToBlock() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(intervenedResponse(
                GuardrailAssessment.builder()
                        .contentPolicy(GuardrailContentPolicyAssessment.builder()
                                .filters(GuardrailContentFilter.builder()
                                        .action(GuardrailContentPolicyAction.BLOCKED)
                                        .build())
                                .build())
                        .build()
        ));
        BedrockGuardrailExecutionAdapter adapter = new BedrockGuardrailExecutionAdapter(client);

        GuardrailExecutionResult result = adapter.execute(new GuardrailExecutionRequest("gr-123", "7", "test input"));

        assertEquals("BLOCK", result.actionCode());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("ANONYMIZED assessment만 있는 intervention은 ALLOW로 반환한다")
    void mapsMaskedInterventionToAllow() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(intervenedResponse(
                anonymizedAssessment(GuardrailSensitiveInformationPolicyAction.ANONYMIZED)
        ));
        BedrockGuardrailExecutionAdapter adapter = new BedrockGuardrailExecutionAdapter(client);

        GuardrailExecutionResult result = adapter.execute(new GuardrailExecutionRequest("gr-123", "7", "test input"));

        assertEquals("ALLOW", result.actionCode());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("BLOCKED와 ANONYMIZED가 함께 있으면 hard block을 우선해 BLOCK으로 반환한다")
    void prioritizesBlockOverMasking() {
        GuardrailAssessment mixedAssessment = GuardrailAssessment.builder()
                .contentPolicy(GuardrailContentPolicyAssessment.builder()
                        .filters(GuardrailContentFilter.builder()
                                .action(GuardrailContentPolicyAction.BLOCKED)
                                .build())
                        .build())
                .sensitiveInformationPolicy(anonymizedAssessment(GuardrailSensitiveInformationPolicyAction.ANONYMIZED)
                        .sensitiveInformationPolicy())
                .build();
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(intervenedResponse(mixedAssessment));
        BedrockGuardrailExecutionAdapter adapter = new BedrockGuardrailExecutionAdapter(client);

        GuardrailExecutionResult result = adapter.execute(new GuardrailExecutionRequest("gr-123", "7", "test input"));

        assertEquals("BLOCK", result.actionCode());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("알 수 없는 intervention policy action은 PROVIDER_RESPONSE_INVALID로 반환한다")
    void mapsUnknownInterventionPolicyActionToInvalidProviderResponse() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(intervenedResponse(
                anonymizedAssessment(GuardrailSensitiveInformationPolicyAction.UNKNOWN_TO_SDK_VERSION)
        ));
        BedrockGuardrailExecutionAdapter adapter = new BedrockGuardrailExecutionAdapter(client);

        GuardrailExecutionResult result = adapter.execute(new GuardrailExecutionRequest("gr-123", "7", "test input"));

        assertNull(result.actionCode());
        assertEquals(GuardrailFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
    }

    @Test
    @DisplayName("action이 없는 ApplyGuardrail 응답은 PROVIDER_RESPONSE_INVALID로 변환한다")
    void mapsMissingActionToInvalidProviderResponse() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenReturn(ApplyGuardrailResponse.builder().build());
        BedrockGuardrailExecutionAdapter adapter = new BedrockGuardrailExecutionAdapter(client);

        GuardrailExecutionResult result = adapter.execute(new GuardrailExecutionRequest("gr-123", "7", "test input"));

        assertNull(result.actionCode());
        assertEquals(GuardrailFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
    }

    @Test
    @DisplayName("Bedrock target 미발견은 TARGET_NOT_FOUND로 변환한다")
    void mapsResourceNotFoundToTargetNotFound() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().build());
        BedrockGuardrailExecutionAdapter adapter = new BedrockGuardrailExecutionAdapter(client);

        GuardrailExecutionResult result = adapter.execute(new GuardrailExecutionRequest("gr-123", "7", "test input"));

        assertEquals(GuardrailFailureCode.TARGET_NOT_FOUND, result.failureCode());
    }

    @Test
    @DisplayName("SDK timeout은 PROVIDER_TIMEOUT으로 변환한다")
    void mapsSdkTimeoutToProviderTimeout() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenThrow(ApiCallTimeoutException.create(1));
        BedrockGuardrailExecutionAdapter adapter = new BedrockGuardrailExecutionAdapter(client);

        GuardrailExecutionResult result = adapter.execute(new GuardrailExecutionRequest("gr-123", "7", "test input"));

        assertEquals(GuardrailFailureCode.PROVIDER_TIMEOUT, result.failureCode());
    }

    private static ApplyGuardrailResponse intervenedResponse(GuardrailAssessment assessment) {
        return ApplyGuardrailResponse.builder()
                .action(GuardrailAction.GUARDRAIL_INTERVENED)
                .assessments(assessment)
                .build();
    }

    private static GuardrailAssessment anonymizedAssessment(GuardrailSensitiveInformationPolicyAction action) {
        return GuardrailAssessment.builder()
                .sensitiveInformationPolicy(GuardrailSensitiveInformationPolicyAssessment.builder()
                        .piiEntities(GuardrailPiiEntityFilter.builder().action(action).build())
                        .build())
                .build();
    }
}

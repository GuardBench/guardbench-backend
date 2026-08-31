package com.guardbench.evaluator.infrastructure.bedrock;

import static com.guardbench.evaluator.infrastructure.bedrock.BedrockGuardrailEvaluatorFixture.anonymizedPiiAssessment;
import static com.guardbench.evaluator.infrastructure.bedrock.BedrockGuardrailEvaluatorFixture.blockedAndAnonymizedAssessment;
import static com.guardbench.evaluator.infrastructure.bedrock.BedrockGuardrailEvaluatorFixture.blockedContentAssessment;
import static com.guardbench.evaluator.infrastructure.bedrock.BedrockGuardrailEvaluatorFixture.executionRequest;
import static com.guardbench.evaluator.infrastructure.bedrock.BedrockGuardrailEvaluatorFixture.intervenedResponse;
import static com.guardbench.evaluator.infrastructure.bedrock.BedrockGuardrailEvaluatorFixture.noInterventionResponse;
import static com.guardbench.evaluator.infrastructure.bedrock.BedrockGuardrailEvaluatorFixture.responseWithoutAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;
import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;

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
class BedrockGuardrailEvaluatorAdapterTest {

    @Mock
    private BedrockRuntimeClient client;

    @Mock
    private BedrockGuardrailEvaluatorStore evaluatorStore;

    @Captor
    private ArgumentCaptor<ApplyGuardrailRequest> requestCaptor;

    @Test
    @DisplayName("Application response를 OUTPUT text로 평가하고 무개입 결과를 ALLOW로 반환한다")
    void evaluatesApplicationResponseAsOutput() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(noInterventionResponse());

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        verify(client).applyGuardrail(requestCaptor.capture());
        ApplyGuardrailRequest sdkRequest = requestCaptor.getValue();
        assertEquals("gr-123", sdkRequest.guardrailIdentifier());
        assertEquals("7", sdkRequest.guardrailVersion());
        assertEquals(GuardrailContentSource.OUTPUT, sdkRequest.source());
        assertEquals(GuardrailOutputScope.INTERVENTIONS, sdkRequest.outputScope());
        assertEquals("application response", sdkRequest.content().getFirst().text().text());
        assertEquals("ALLOW", result.actionCode());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("BLOCKED assessment가 있는 intervention은 BLOCK으로 반환한다")
    void mapsBlockedInterventionToBlock() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenReturn(intervenedResponse(blockedContentAssessment()));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals("BLOCK", result.actionCode());
    }

    @Test
    @DisplayName("ANONYMIZED assessment만 있는 intervention은 ALLOW로 반환한다")
    void mapsMaskedInterventionToAllow() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(intervenedResponse(
                anonymizedPiiAssessment(GuardrailSensitiveInformationPolicyAction.ANONYMIZED)));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals("ALLOW", result.actionCode());
    }

    @Test
    @DisplayName("BLOCKED와 ANONYMIZED가 함께 있으면 BLOCK을 우선한다")
    void prioritizesBlockOverMasking() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenReturn(intervenedResponse(blockedAndAnonymizedAssessment()));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals("BLOCK", result.actionCode());
    }

    @Test
    @DisplayName("알 수 없는 assessment action은 PROVIDER_RESPONSE_INVALID로 반환한다")
    void mapsUnknownAssessmentActionToInvalidResponse() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(intervenedResponse(
                anonymizedPiiAssessment(GuardrailSensitiveInformationPolicyAction.UNKNOWN_TO_SDK_VERSION)));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
    }

    @Test
    @DisplayName("action이 없는 응답은 PROVIDER_RESPONSE_INVALID로 변환한다")
    void mapsMissingActionToInvalidResponse() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class))).thenReturn(responseWithoutAction());

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
    }

    @Test
    @DisplayName("Evaluator reference 미발견은 EVALUATOR_NOT_FOUND로 변환한다")
    void mapsMissingEvaluatorReference() {
        when(evaluatorStore.findByReference("evaluator-ref")).thenReturn(Optional.empty());

        EvaluatorExecutionResult result = new BedrockGuardrailEvaluatorAdapter(client, evaluatorStore)
                .evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.EVALUATOR_NOT_FOUND, result.failureCode());
    }

    @Test
    @DisplayName("Bedrock resource 미발견은 EVALUATOR_NOT_FOUND로 변환한다")
    void mapsResourceNotFoundToEvaluatorNotFound() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().build());

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.EVALUATOR_NOT_FOUND, result.failureCode());
    }

    @Test
    @DisplayName("SDK timeout은 PROVIDER_TIMEOUT으로 변환한다")
    void mapsSdkTimeoutToProviderTimeout() {
        when(client.applyGuardrail(any(ApplyGuardrailRequest.class)))
                .thenThrow(ApiCallTimeoutException.create(1));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.PROVIDER_TIMEOUT, result.failureCode());
    }

    private BedrockGuardrailEvaluatorAdapter adapter() {
        when(evaluatorStore.findByReference("evaluator-ref"))
                .thenReturn(Optional.of(new BedrockGuardrailEvaluatorStore.BedrockGuardrailEvaluator(
                        "evaluator-ref", "gr-123", "7")));
        return new BedrockGuardrailEvaluatorAdapter(client, evaluatorStore);
    }
}

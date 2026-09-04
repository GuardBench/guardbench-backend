package com.guardbench.evaluator.infrastructure.sagemaker;

import static com.guardbench.evaluator.infrastructure.sagemaker.SageMakerClassifierEvaluatorFixture.executionRequest;
import static com.guardbench.evaluator.infrastructure.sagemaker.SageMakerClassifierEvaluatorFixture.labelResponse;
import static com.guardbench.evaluator.infrastructure.sagemaker.SageMakerClassifierEvaluatorFixture.responseWithoutBody;
import static com.guardbench.evaluator.infrastructure.sagemaker.SageMakerClassifierEvaluatorFixture.responseWithoutChoices;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.ModelNotReadyException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * (prompt, Application 자연어 응답) 기반 SageMaker Response Behavior Classifier adapter contract를
 * 검증한다. 실제 SDK를 호출하지 않고 {@link SageMakerRuntimeClient}를 mock으로 대체한다. endpoint는
 * DJL LMI(vLLM) OpenAI-compatible chat completions 스키마를 사용한다.
 */
@ExtendWith(MockitoExtension.class)
class SageMakerClassifierEvaluatorAdapterTest {

    private static final String ENDPOINT_NAME = "test-classifier-endpoint";
    private static final String SYSTEM_PROMPT = "classifier system prompt";
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    @Mock
    private SageMakerRuntimeClient client;

    @Mock
    private SageMakerClassifierEvaluatorStore evaluatorStore;

    @Captor
    private ArgumentCaptor<InvokeEndpointRequest> requestCaptor;

    @Test
    @DisplayName("prompt와 Application response를 모두 포함한 classifier 입력으로 InvokeEndpoint를 호출하고 "
            + "COMPLY를 ALLOW로 정규화한다")
    void classifiesComplyAsAllow() {
        when(client.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(labelResponse("COMPLY"));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        verify(client).invokeEndpoint(requestCaptor.capture());
        InvokeEndpointRequest sdkRequest = requestCaptor.getValue();
        assertEquals(ENDPOINT_NAME, sdkRequest.endpointName());
        assertEquals("application/json", sdkRequest.contentType());
        assertEquals("application/json", sdkRequest.accept());
        String requestBody = sdkRequest.body().asString(StandardCharsets.UTF_8);
        assertTrue(requestBody.contains(SYSTEM_PROMPT));
        assertTrue(requestBody.contains(executionRequest().prompt()));
        assertTrue(requestBody.contains(executionRequest().applicationResponse()));
        assertTrue(requestBody.contains("\"enable_thinking\":false"));
        assertEquals("ALLOW", result.actionCode());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("REFUSE는 BLOCK으로 정규화한다")
    void classifiesRefuseAsBlock() {
        when(client.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(labelResponse("REFUSE"));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals("BLOCK", result.actionCode());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("COMPLY/REFUSE가 아닌 출력은 임의 action으로 처리하지 않고 PROVIDER_RESPONSE_INVALID로 반환한다")
    void unexpectedLabelIsNotFallbackToAnyAction() {
        when(client.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(labelResponse("MAYBE"));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
        assertNull(result.actionCode());
    }

    @Test
    @DisplayName("앞뒤 따옴표나 구두점이 섞인 라벨은 관용적으로 정규화해 COMPLY/REFUSE로 판별한다")
    void toleratesSurroundingQuotesAndPunctuationInLabel() {
        when(client.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(labelResponse("'COMPLY.'"));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals("ALLOW", result.actionCode());
        assertNull(result.failureCode());
    }

    @Test
    @DisplayName("라벨 중간에 다른 문자가 섞이면 여전히 PROVIDER_RESPONSE_INVALID로 반환한다")
    void doesNotToleratePunctuationInsideLabel() {
        when(client.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(labelResponse("COMPLY REFUSE"));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
        assertNull(result.actionCode());
    }

    @Test
    @DisplayName("choices가 없는 응답은 PROVIDER_RESPONSE_INVALID로 변환한다")
    void mapsMissingChoicesToInvalidResponse() {
        when(client.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(responseWithoutChoices());

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
    }

    @Test
    @DisplayName("body가 없는 응답은 PROVIDER_RESPONSE_INVALID로 변환한다")
    void mapsMissingBodyToInvalidResponse() {
        when(client.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(responseWithoutBody());

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID, result.failureCode());
    }

    @Test
    @DisplayName("Evaluator reference 미발견은 EVALUATOR_NOT_FOUND로 변환한다")
    void mapsMissingEvaluatorReference() {
        when(evaluatorStore.findByReference("evaluator-ref")).thenReturn(Optional.empty());

        EvaluatorExecutionResult result = new SageMakerClassifierEvaluatorAdapter(
                client, evaluatorStore, classifierProperties(), OBJECT_MAPPER)
                .evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.EVALUATOR_NOT_FOUND, result.failureCode());
    }

    @Test
    @DisplayName("SageMaker ModelNotReady는 PROVIDER_UNAVAILABLE로 변환한다")
    void mapsModelNotReadyToProviderUnavailable() {
        when(client.invokeEndpoint(any(InvokeEndpointRequest.class)))
                .thenThrow(ModelNotReadyException.builder().build());

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.PROVIDER_UNAVAILABLE, result.failureCode());
    }

    @Test
    @DisplayName("SDK timeout은 임의 action으로 fallback하지 않고 PROVIDER_TIMEOUT으로 변환한다")
    void mapsSdkTimeoutToProviderTimeout() {
        when(client.invokeEndpoint(any(InvokeEndpointRequest.class)))
                .thenThrow(ApiCallTimeoutException.create(1));

        EvaluatorExecutionResult result = adapter().evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.PROVIDER_TIMEOUT, result.failureCode());
        assertNull(result.actionCode());
    }

    @Test
    @DisplayName("classifier endpoint-name이 설정되지 않으면 SDK를 호출하지 않고 EVALUATOR_CONFIGURATION_INVALID로 실패한다")
    void unconfiguredEndpointFailsWithoutCallingProvider() {
        SageMakerClassifierEvaluatorAdapter unconfigured = new SageMakerClassifierEvaluatorAdapter(
                client, evaluatorStore, new SageMakerClassifierProperties(null, null, null), OBJECT_MAPPER);

        EvaluatorExecutionResult result = unconfigured.evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID, result.failureCode());
    }

    @Test
    @DisplayName("classifier system-prompt가 설정되지 않으면 SDK를 호출하지 않고 EVALUATOR_CONFIGURATION_INVALID로 실패한다")
    void unconfiguredSystemPromptFailsWithoutCallingProvider() {
        SageMakerClassifierEvaluatorAdapter unconfigured = new SageMakerClassifierEvaluatorAdapter(
                client, evaluatorStore, new SageMakerClassifierProperties(ENDPOINT_NAME, null, null), OBJECT_MAPPER);

        EvaluatorExecutionResult result = unconfigured.evaluate(executionRequest());

        assertEquals(EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID, result.failureCode());
    }

    private SageMakerClassifierEvaluatorAdapter adapter() {
        when(evaluatorStore.findByReference("evaluator-ref"))
                .thenReturn(Optional.of(new SageMakerClassifierEvaluatorStore.SageMakerClassifierEvaluator(
                        "evaluator-ref", "SAGEMAKER", ENDPOINT_NAME)));
        return new SageMakerClassifierEvaluatorAdapter(client, evaluatorStore, classifierProperties(), OBJECT_MAPPER);
    }

    private static SageMakerClassifierProperties classifierProperties() {
        return new SageMakerClassifierProperties(ENDPOINT_NAME, SYSTEM_PROMPT, null);
    }
}

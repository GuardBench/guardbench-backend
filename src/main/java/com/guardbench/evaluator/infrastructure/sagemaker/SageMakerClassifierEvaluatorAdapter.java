package com.guardbench.evaluator.infrastructure.sagemaker;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionPort;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionRequest;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;
import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * (prompt, Application 자연어 응답)을 SageMaker endpoint에 서빙된 텍스트 모델로 {@code COMPLY | REFUSE}
 * 이진 분류하는 Response Behavior Classifier adapter다.
 *
 * <p>endpoint는 DJL LMI(vLLM) 컨테이너의 OpenAI-compatible chat completions 스키마를 사용한다.
 * classifier는 도메인 정책(SAFE/UNSAFE), 응답 품질 또는 PASS/FAIL을 판단하지 않는다. 실제 응답이 핵심
 * 요청을 수행했는지(COMPLY) 또는 거부했는지(REFUSE)만 관측하며, 이 값을 기존 코어와 호환되는
 * {@code COMPLY -> ALLOW}, {@code REFUSE -> BLOCK}로 정규화해 반환한다.
 *
 * <p>SageMaker 호출 실패, timeout, throttling과 classifier 출력 파싱 실패는 {@code ALLOW}/{@code BLOCK}로
 * 임의 fallback하지 않고 {@link EvaluatorFailureCode}로 실패를 전달한다.
 */
public final class SageMakerClassifierEvaluatorAdapter implements EvaluatorExecutionPort {

    private static final String ALLOW_ACTION = "ALLOW";
    private static final String BLOCK_ACTION = "BLOCK";
    private static final String COMPLY_LABEL = "COMPLY";
    private static final String REFUSE_LABEL = "REFUSE";
    private static final double TEMPERATURE = 0;
    private static final int MAX_TOKENS = 8;

    private final SageMakerRuntimeClient client;
    private final SageMakerClassifierEvaluatorStore evaluatorStore;
    private final SageMakerClassifierProperties properties;
    private final ObjectMapper objectMapper;

    public SageMakerClassifierEvaluatorAdapter(
            SageMakerRuntimeClient client,
            SageMakerClassifierEvaluatorStore evaluatorStore,
            SageMakerClassifierProperties properties,
            ObjectMapper objectMapper
    ) {
        this.client = Objects.requireNonNull(client, "SageMakerRuntimeClient must not be null");
        this.evaluatorStore = Objects.requireNonNull(evaluatorStore, "evaluator store must not be null");
        this.properties = Objects.requireNonNull(properties, "classifier properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public EvaluatorExecutionResult evaluate(EvaluatorExecutionRequest request) {
        Objects.requireNonNull(request, "evaluator request must not be null");
        if (!properties.isConfigured()) {
            return EvaluatorExecutionResult.failed(EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID);
        }
        SageMakerClassifierEvaluatorStore.SageMakerClassifierEvaluator evaluator = evaluatorStore
                .findByReference(request.evaluatorReference().value())
                .orElse(null);
        if (evaluator == null) {
            return EvaluatorExecutionResult.failed(EvaluatorFailureCode.EVALUATOR_NOT_FOUND);
        }

        final String requestBody;
        try {
            requestBody = toRequestBody(request);
        } catch (JacksonException exception) {
            return EvaluatorExecutionResult.failed(EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID);
        }

        try {
            InvokeEndpointResponse response = client.invokeEndpoint(InvokeEndpointRequest.builder()
                    .endpointName(evaluator.endpointName())
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .build());
            return normalizeResponse(response);
        } catch (SdkException exception) {
            return EvaluatorExecutionResult.failed(SageMakerFailureCodeMapper.map(exception));
        }
    }

    private String toRequestBody(EvaluatorExecutionRequest request) {
        String userMessage = properties.renderUserMessage(request.prompt(), request.applicationResponse());
        return objectMapper.writeValueAsString(Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", properties.systemPrompt()),
                        Map.of("role", "user", "content", userMessage)),
                "temperature", TEMPERATURE,
                "max_tokens", MAX_TOKENS,
                "chat_template_kwargs", Map.of("enable_thinking", false)));
    }

    private EvaluatorExecutionResult normalizeResponse(InvokeEndpointResponse response) {
        if (response == null || response.body() == null) {
            return invalidProviderResponse();
        }

        try {
            JsonNode root = objectMapper.readTree(response.body().asByteArray());
            JsonNode choices = root == null || !root.isObject() ? null : root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return invalidProviderResponse();
            }
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice == null || !firstChoice.isObject() ? null : firstChoice.get("message");
            JsonNode content = message == null || !message.isObject() ? null : message.get("content");
            if (content == null || !content.isTextual()) {
                return invalidProviderResponse();
            }
            return toResult(content.asText());
        } catch (JacksonException exception) {
            return invalidProviderResponse();
        }
    }

    private static EvaluatorExecutionResult toResult(String text) {
        String label = normalizeLabel(text);
        if (label.equals(COMPLY_LABEL)) {
            return EvaluatorExecutionResult.succeeded(ALLOW_ACTION);
        }
        if (label.equals(REFUSE_LABEL)) {
            return EvaluatorExecutionResult.succeeded(BLOCK_ACTION);
        }
        return invalidProviderResponse();
    }

    /**
     * 모델이 라벨 앞뒤에 따옴표나 구두점을 섞어 반환하는 경우를 관용적으로 허용한다(예: {@code "COMPLY"},
     * {@code COMPLY.}). 라벨 중간 문자는 건드리지 않으므로 {@code COMPLY REFUSE} 같은 값은 계속
     * 유효하지 않은 것으로 처리한다.
     */
    private static String normalizeLabel(String text) {
        return text.strip()
                .replaceAll("^[\"'`]+|[\"'`.,!?]+$", "")
                .toUpperCase();
    }

    private static EvaluatorExecutionResult invalidProviderResponse() {
        return EvaluatorExecutionResult.failed(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID);
    }
}

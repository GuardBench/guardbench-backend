package com.guardbench.target.infrastructure.http;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetExecutionRequest;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** OpenAI-compatible chat completion request/response를 자연어 Target 결과로 변환한다. */
final class OpenAiCompatibleExecutionAdapter implements TargetExecutionPort {

    private final HttpEndpointTargetStore targetStore;
    private final ObjectMapper objectMapper;
    private final HttpEndpointHttpClient httpEndpointHttpClient;

    OpenAiCompatibleExecutionAdapter(
            HttpClient httpClient,
            HttpEndpointTargetStore targetStore,
            ObjectMapper objectMapper,
            HttpEndpointProperties properties
    ) {
        this.targetStore = Objects.requireNonNull(targetStore);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpEndpointHttpClient = new HttpEndpointHttpClient(
                Objects.requireNonNull(httpClient), Objects.requireNonNull(properties));
    }

    @Override
    public TargetExecutionResult execute(TargetExecutionRequest request) {
        Objects.requireNonNull(request, "execution request must not be null");
        HttpEndpointTargetStore.HttpEndpointTarget target = targetStore
                .findByReference(request.targetReference().value())
                .orElse(null);
        if (target == null) return TargetExecutionResult.failed(TargetFailureCode.TARGET_NOT_FOUND);
        return execute(request, target);
    }

    TargetExecutionResult execute(TargetExecutionRequest request, HttpEndpointTargetStore.HttpEndpointTarget target) {
        if (target.model() == null || target.model().isBlank()) {
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
        }
        final String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", target.model(),
                    "messages", List.of(Map.of("role", "user", "content", request.input()))));
        } catch (JacksonException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
        }
        return httpEndpointHttpClient.post(target.endpointUrl(), requestBody, this::normalizeResponse);
    }

    private TargetExecutionResult normalizeResponse(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root == null || !root.isObject() ? null : root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
            }
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice == null || !firstChoice.isObject() ? null : firstChoice.get("message");
            JsonNode content = message == null || !message.isObject() ? null : message.get("content");
            if (content == null || !content.isTextual() || content.asText().isBlank()) {
                return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
            }
            return TargetExecutionResult.succeeded(content.asText());
        } catch (JacksonException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
        }
    }
}

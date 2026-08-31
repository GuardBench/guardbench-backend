package com.guardbench.target.infrastructure.http;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.Objects;

import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetExecutionRequest;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Snapshot input을 HTTP Application에 전달하고 자연어 응답을 provider-independent 결과로 반환한다. */
public final class HttpEndpointExecutionAdapter implements TargetExecutionPort {

    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String RESPONSE_FIELD = "response";

    private final HttpEndpointTargetStore targetStore;
    private final ObjectMapper objectMapper;
    private final HttpEndpointHttpClient httpEndpointHttpClient;

    public HttpEndpointExecutionAdapter(
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
        if (target == null) {
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_NOT_FOUND);
        }
        return execute(request, target);
    }

    TargetExecutionResult execute(TargetExecutionRequest request, HttpEndpointTargetStore.HttpEndpointTarget target) {
        final String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of("input", request.input()));
        } catch (JacksonException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
        }
        return httpEndpointHttpClient.post(target.endpointUrl(), requestBody, this::normalizeResponse);
    }

    private TargetExecutionResult normalizeResponse(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject() || root.size() != 1) {
                return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
            }
            JsonNode responseNode = root.get(RESPONSE_FIELD);
            if (responseNode == null || !responseNode.isTextual() || responseNode.asText().isBlank()) {
                return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
            }
            return TargetExecutionResult.succeeded(responseNode.asText());
        } catch (JacksonException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
        }
    }
}

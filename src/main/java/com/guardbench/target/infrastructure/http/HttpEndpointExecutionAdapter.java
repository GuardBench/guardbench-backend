package com.guardbench.target.infrastructure.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    private final HttpClient httpClient;
    private final HttpEndpointTargetStore targetStore;
    private final ObjectMapper objectMapper;
    private final HttpEndpointProperties properties;

    public HttpEndpointExecutionAdapter(
            HttpClient httpClient,
            HttpEndpointTargetStore targetStore,
            ObjectMapper objectMapper,
            HttpEndpointProperties properties
    ) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.targetStore = Objects.requireNonNull(targetStore);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.properties = Objects.requireNonNull(properties);
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

        final URI endpoint;
        try {
            endpoint = HttpEndpointUrlValidator.parse(target.endpointUrl());
            HttpEndpointUrlValidator.validateResolvedAddress(endpoint, properties.allowPrivateAddresses());
        } catch (UnknownHostException | IllegalArgumentException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
        }

        final String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of("input", request.input()));
        } catch (JacksonException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(properties.requestTimeoutMs()))
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("Accept", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            return normalizeResponse(response);
        } catch (java.net.http.HttpTimeoutException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_TIMEOUT);
        } catch (IOException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_UNAVAILABLE);
        }
    }

    private TargetExecutionResult normalizeResponse(HttpResponse<InputStream> response) throws IOException {
        int statusCode = response.statusCode();
        if (statusCode == 404) {
            closeBody(response.body());
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_NOT_FOUND);
        }
        if (statusCode == 401 || statusCode == 403) {
            closeBody(response.body());
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_ACCESS_DENIED);
        }
        if (statusCode >= 400 && statusCode < 500) {
            closeBody(response.body());
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
        }
        if (statusCode >= 500) {
            closeBody(response.body());
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_UNAVAILABLE);
        }
        if (statusCode < 200 || statusCode >= 300) {
            closeBody(response.body());
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
        }
        if (!isJson(response)) {
            closeBody(response.body());
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
        }

        byte[] body = readBoundedBody(response.body());
        if (body == null || body.length == 0) {
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
        }
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

    private static boolean isJson(HttpResponse<InputStream> response) {
        return response.headers().firstValue("Content-Type")
                .map(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(JSON_CONTENT_TYPE))
                .orElse(false);
    }

    private byte[] readBoundedBody(InputStream body) throws IOException {
        if (body == null) {
            return null;
        }
        try (InputStream input = body; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > properties.maxResponseBytes()) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void closeBody(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException ignored) {
            // 상태 코드가 이미 결정된 응답이므로 원문을 오류로 노출하지 않는다.
        }
    }
}

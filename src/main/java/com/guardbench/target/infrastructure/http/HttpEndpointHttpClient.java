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
import java.util.Locale;
import java.util.Objects;

import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;

/** Generic HTTP Target와 OpenAI-compatible Adapter가 공유하는 안전한 HTTP 호출 경계다. */
final class HttpEndpointHttpClient {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final HttpClient httpClient;
    private final HttpEndpointProperties properties;

    HttpEndpointHttpClient(HttpClient httpClient, HttpEndpointProperties properties) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.properties = Objects.requireNonNull(properties);
    }

    TargetExecutionResult post(String endpointUrl, String requestBody, ResponseParser responseParser) {
        final URI endpoint;
        try {
            endpoint = HttpEndpointUrlValidator.parse(endpointUrl);
            HttpEndpointUrlValidator.validateResolvedAddress(
                    endpoint,
                    properties.allowPrivateAddresses(),
                    properties.allowedPrivateHostnames());
        } catch (UnknownHostException | IllegalArgumentException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(properties.requestTimeoutMs()))
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("Accept", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            TargetExecutionResult statusFailure = statusFailure(response);
            if (statusFailure != null) {
                closeBody(response.body());
                return statusFailure;
            }
            if (!isJson(response)) {
                closeBody(response.body());
                return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
            }
            byte[] body = readBoundedBody(response.body());
            if (body == null || body.length == 0) {
                return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
            }
            return responseParser.parse(body);
        } catch (java.net.http.HttpTimeoutException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_TIMEOUT);
        } catch (IOException exception) {
            return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_UNAVAILABLE);
        }
    }

    private static TargetExecutionResult statusFailure(HttpResponse<InputStream> response) {
        int statusCode = response.statusCode();
        if (statusCode == 404) return TargetExecutionResult.failed(TargetFailureCode.TARGET_NOT_FOUND);
        if (statusCode == 401 || statusCode == 403) return TargetExecutionResult.failed(TargetFailureCode.TARGET_ACCESS_DENIED);
        if (statusCode >= 400 && statusCode < 500) return TargetExecutionResult.failed(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
        if (statusCode >= 500) return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_UNAVAILABLE);
        if (statusCode < 200 || statusCode >= 300) return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
        return null;
    }

    private static boolean isJson(HttpResponse<InputStream> response) {
        return response.headers().firstValue("Content-Type")
                .map(value -> value.toLowerCase(Locale.ROOT).startsWith(JSON_CONTENT_TYPE))
                .orElse(false);
    }

    private byte[] readBoundedBody(InputStream body) throws IOException {
        if (body == null) return null;
        try (InputStream input = body; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > properties.maxResponseBytes()) return null;
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void closeBody(InputStream body) {
        if (body == null) return;
        try {
            body.close();
        } catch (IOException ignored) {
            // 공개 오류에는 provider 원문을 포함하지 않는다.
        }
    }

    @FunctionalInterface
    interface ResponseParser {
        TargetExecutionResult parse(byte[] body);
    }
}

package com.guardbench.target.infrastructure.http;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** HTTP Application Target worker 호출 설정이다. */
@ConfigurationProperties(prefix = "guardbench.http-endpoint")
public record HttpEndpointProperties(
        long connectTimeoutMs,
        long requestTimeoutMs,
        int maxResponseBytes,
        boolean allowPrivateAddresses,
        List<String> allowedPrivateHostnames
) {

    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 5_000L;
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 15_000L;
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 1_048_576;

    public HttpEndpointProperties {
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        }
        if (requestTimeoutMs <= 0) {
            requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;
        }
        if (maxResponseBytes <= 0) {
            maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;
        }
        allowedPrivateHostnames = allowedPrivateHostnames == null
                ? List.of()
                : allowedPrivateHostnames.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(hostname -> !hostname.isEmpty())
                        .map(hostname -> hostname.toLowerCase(Locale.ROOT))
                        .map(HttpEndpointProperties::withoutTrailingDot)
                        .distinct()
                        .toList();
        if (connectTimeoutMs > requestTimeoutMs) {
            throw new IllegalArgumentException(
                    "guardbench.http-endpoint.connect-timeout-ms must not exceed request-timeout-ms");
        }
    }

    private static String withoutTrailingDot(String hostname) {
        return hostname.endsWith(".") ? hostname.substring(0, hostname.length() - 1) : hostname;
    }
}

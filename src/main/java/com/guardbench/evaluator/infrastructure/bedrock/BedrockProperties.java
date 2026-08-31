package com.guardbench.evaluator.infrastructure.bedrock;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bedrock Evaluator Provider 호출 설정이다. */
@ConfigurationProperties(prefix = "guardbench.bedrock")
record BedrockProperties(
        String region,
        String endpointOverride,
        long apiCallTimeoutMs,
        long apiCallAttemptTimeoutMs,
        int maxAttempts
) {

    private static final long CLAIM_LEASE_MS = 45_000L;
    private static final long DEFAULT_API_CALL_TIMEOUT_MS = 15_000L;
    private static final long DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MS = 5_000L;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    BedrockProperties {
        if (region == null || region.isBlank()) {
            region = "ap-northeast-2";
        }
        if (apiCallTimeoutMs <= 0) {
            apiCallTimeoutMs = DEFAULT_API_CALL_TIMEOUT_MS;
        }
        if (apiCallAttemptTimeoutMs <= 0) {
            apiCallAttemptTimeoutMs = DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MS;
        }
        if (maxAttempts <= 0) {
            maxAttempts = DEFAULT_MAX_ATTEMPTS;
        }
        if (apiCallTimeoutMs >= CLAIM_LEASE_MS) {
            throw new IllegalArgumentException(
                    "guardbench.bedrock.api-call-timeout-ms must stay below the 45s execution claim lease");
        }
        if (apiCallAttemptTimeoutMs > apiCallTimeoutMs) {
            throw new IllegalArgumentException(
                    "guardbench.bedrock.api-call-attempt-timeout-ms must not exceed api-call-timeout-ms");
        }
    }
}

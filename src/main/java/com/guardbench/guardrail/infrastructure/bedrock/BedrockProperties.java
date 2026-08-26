package com.guardbench.guardrail.infrastructure.bedrock;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bedrock Provider 호출 설정이다.
 *
 * <p>ADR 0005: Provider 호출은 재시도를 포함해 전체 15초 안에 끝나야 한다.
 * 전체 호출 한도가 execution claim lease(45초)를 넘으면 lease 만료 후에도
 * 호출이 진행되어 다른 Worker와 중복 실행될 수 있으므로 기동 시 검증한다.
 *
 * <p>AWS credential은 DefaultCredentialsProvider 체인으로 공급하며 별도 속성은 두지 않는다.
 */
@ConfigurationProperties(prefix = "guardbench.bedrock")
record BedrockProperties(
        /** AWS region (기본 ap-northeast-2). */
        String region,
        /** Bedrock endpoint override. null이면 SDK 기본값. */
        String endpointOverride,
        /** 재시도를 포함한 전체 호출 한도(ms). ADR 0005 기준 15초. */
        long apiCallTimeoutMs,
        /** 개별 시도 한도(ms). */
        long apiCallAttemptTimeoutMs,
        /** 재시도를 포함한 최대 시도 횟수. */
        int maxAttempts
) {

    /** ADR 0005 execution claim lease(45초). 전체 호출 한도는 이 값을 넘을 수 없다. */
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

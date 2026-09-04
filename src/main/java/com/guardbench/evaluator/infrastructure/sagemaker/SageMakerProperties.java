package com.guardbench.evaluator.infrastructure.sagemaker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SageMaker Runtime Provider 호출 한도다. (ADR 0005: 재시도 포함 전체 15초, claim lease 45초 미만)
 *
 * <p>Provider business retry 소유권은 Worker claim retry({@code ExecuteTestRunService.
 * MAX_EXECUTION_ATTEMPTS}, 최대 3회) 한 계층에만 둔다. SDK 자체 retry({@code maxAttempts})와 Worker
 * claim retry가 동시에 활성화되면 실제 호출 횟수가 두 값의 곱으로 증폭된다(예: SDK 4회 x claim 3회 =
 * 12회). 이를 방지하기 위해 SDK {@code maxAttempts} 기본값은 재시도 없음(1)으로 고정하고, transient
 * 실패(PROVIDER_UNAVAILABLE/PROVIDER_TIMEOUT) 재시도는 Worker claim retry가 전담한다.
 */
@ConfigurationProperties(prefix = "guardbench.sagemaker")
record SageMakerProperties(
        String region,
        String endpointOverride,
        long apiCallTimeoutMs,
        long apiCallAttemptTimeoutMs,
        int maxAttempts
) {

    private static final long CLAIM_LEASE_MS = 45_000L;
    private static final long DEFAULT_API_CALL_TIMEOUT_MS = 15_000L;
    private static final long DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MS = 5_000L;
    private static final int DEFAULT_MAX_ATTEMPTS = 1;

    SageMakerProperties {
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
                    "guardbench.sagemaker.api-call-timeout-ms must stay below the 45s execution claim lease");
        }
        if (apiCallAttemptTimeoutMs > apiCallTimeoutMs) {
            throw new IllegalArgumentException(
                    "guardbench.sagemaker.api-call-attempt-timeout-ms must not exceed api-call-timeout-ms");
        }
    }
}

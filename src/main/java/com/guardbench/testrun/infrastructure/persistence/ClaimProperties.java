package com.guardbench.testrun.infrastructure.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Execution/Resolution claim lease 운영값을 외부화한 설정이다.
 *
 * <p>ADR 0008이 승인한 claim lease 초기값(45초)을 기본값으로 유지한다.
 * {@link PostgresExecutionClaimAdapter}와 {@link PostgresResolutionClaimAdapter}가
 * 같은 값을 공유하며, 운영 환경은 별도 설정 없이 기존 45초 동작을 그대로 사용한다.
 *
 * <p>이 설정은 claim semantics를 바꾸지 않는 testability 목적의 외부화다.
 * Reliability integration test에서만 축소된 lease 값을 주입해
 * visibility timeout과의 관계(visibility &lt; lease 등)를 짧은 시간 안에 재현한다.
 */
@ConfigurationProperties(prefix = "guardbench.claim")
public record ClaimProperties(
        /** Execution/Resolution claim lease 초. ADR 0008 초기값은 45초다. */
        Integer leaseSeconds
) {

    private static final int DEFAULT_LEASE_SECONDS = 45;

    public ClaimProperties {
        if (leaseSeconds == null || leaseSeconds <= 0) {
            leaseSeconds = DEFAULT_LEASE_SECONDS;
        }
    }
}

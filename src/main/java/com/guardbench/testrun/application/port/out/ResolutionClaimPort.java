package com.guardbench.testrun.application.port.out;

import java.util.UUID;

/**
 * TestRun resolution claim lease를 관리하는 consumer-owned 아웃바운드 Port다.
 * lease 시각 비교는 PostgreSQL clock_timestamp()를 사용한다.
 */
public interface ResolutionClaimPort {

    /**
     * claim이 없거나 lease가 만료되었을 때 원자적으로 새 token을 선점한다.
     * attemptCount는 기존 claim의 count에 1을 더한 값이다.
     */
    ClaimResult tryAcquire(long testRunId);

    /**
     * 해당 token이 현재 유효한 claim인지 확인한다.
     * lease_until > clock_timestamp() AND claim_token = token 일 때만 true를 반환한다.
     */
    boolean isHeldBy(long testRunId, UUID claimToken);
}

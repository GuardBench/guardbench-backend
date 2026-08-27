package com.guardbench.testrun.application.port.out;

import java.util.UUID;

/**
 * TestExecution claim lease를 관리하는 consumer-owned 아웃바운드 Port다.
 * lease 시각 비교는 PostgreSQL clock_timestamp()를 사용한다.
 */
public interface ExecutionClaimPort {

    /**
     * claim이 없거나 lease가 만료되었을 때 원자적으로 새 token을 선점한다.
     */
    ClaimResult tryAcquire(long snapshotId, String targetType);

    /**
     * 해당 token이 현재 유효한 claim인지 확인한다.
     */
    boolean isHeldBy(long snapshotId, String targetType, UUID claimToken);
}

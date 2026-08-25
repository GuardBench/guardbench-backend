package com.guardbench.testrun.application.port.out;

import java.util.UUID;

/**
 * Claim 선점 시도의 결과를 나타내는 불변 값이다.
 */
public sealed interface ClaimResult {

    /**
     * 성공적으로 lease를 선점하였다.
     */
    record Acquired(UUID claimToken, int attemptCount) implements ClaimResult {
    }

    /**
     * 다른 Worker가 유효한 lease를 보유하고 있어 선점에 실패하였다.
     */
    record AlreadyHeld() implements ClaimResult {
    }
}

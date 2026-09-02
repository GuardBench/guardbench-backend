package com.guardbench.testrun.infrastructure.messaging.reliability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;

/**
 * Issue #153: 실제 {@link ExecutionClaimPort} 구현체를 감싸 {@code tryAcquire} 호출 결과를
 * snapshotId별로 계수하는 Decorator다.
 *
 * <p>SQS delivery(재전달)와 claim 획득(Provider invocation의 선행 조건)을 분리 관측하기 위해 쓴다.
 * {@code tryAcquire} 호출 횟수는 SQS가 이 메시지를 몇 번 receive해서 처리 시도했는지를 의미하고,
 * 그중 {@link ClaimResult.Acquired}로 성공한 횟수만 실제로 {@code ExecuteTestRunService}가
 * Provider를 호출하는 경로로 이어진다. {@link ClaimResult.AlreadyHeld}는 claim 경합으로
 * Provider 호출 없이 즉시 반환된 시도다.
 */
public final class CountingExecutionClaimPortDecorator implements ExecutionClaimPort {

    private final ExecutionClaimPort delegate;
    private final Map<Long, AtomicInteger> tryAcquireAttempts = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> alreadyHeldCounts = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> acquiredCounts = new ConcurrentHashMap<>();

    public CountingExecutionClaimPortDecorator(ExecutionClaimPort delegate) {
        this.delegate = delegate;
    }

    @Override
    public ClaimResult tryAcquire(long snapshotId) {
        tryAcquireAttempts.computeIfAbsent(snapshotId, ignored -> new AtomicInteger(0)).incrementAndGet();
        ClaimResult result = delegate.tryAcquire(snapshotId);
        if (result instanceof ClaimResult.AlreadyHeld) {
            alreadyHeldCounts.computeIfAbsent(snapshotId, ignored -> new AtomicInteger(0)).incrementAndGet();
        } else {
            acquiredCounts.computeIfAbsent(snapshotId, ignored -> new AtomicInteger(0)).incrementAndGet();
        }
        return result;
    }

    @Override
    public boolean isHeldBy(long snapshotId, UUID claimToken) {
        return delegate.isHeldBy(snapshotId, claimToken);
    }

    /** 해당 snapshot에 대해 {@code tryAcquire}가 총 몇 번 호출되었는지(=claim 시도 횟수). */
    public int tryAcquireAttemptsFor(long snapshotId) {
        return countFor(tryAcquireAttempts, snapshotId);
    }

    /** 해당 snapshot에 대해 {@code AlreadyHeld}가 발생한 횟수. */
    public int alreadyHeldCountFor(long snapshotId) {
        return countFor(alreadyHeldCounts, snapshotId);
    }

    /** 해당 snapshot에 대해 claim을 실제로 획득(Acquired)한 횟수. */
    public int acquiredCountFor(long snapshotId) {
        return countFor(acquiredCounts, snapshotId);
    }

    public void reset() {
        tryAcquireAttempts.clear();
        alreadyHeldCounts.clear();
        acquiredCounts.clear();
    }

    private static int countFor(Map<Long, AtomicInteger> counts, long snapshotId) {
        AtomicInteger counter = counts.get(snapshotId);
        return counter == null ? 0 : counter.get();
    }
}

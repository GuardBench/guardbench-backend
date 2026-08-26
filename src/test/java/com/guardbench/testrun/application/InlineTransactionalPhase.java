package com.guardbench.testrun.application;

import com.guardbench.testrun.application.port.out.TransactionalPhasePort;

/**
 * 단위 테스트용 {@link TransactionalPhasePort} 테스트 더블이다.
 *
 * <p>phase를 즉시 실행하고 실행 횟수를 기록한다.
 * 실제 rollback 의미는 PostgreSQL 통합 테스트에서 검증한다.
 */
public final class InlineTransactionalPhase implements TransactionalPhasePort {

    private int phaseCount;

    @Override
    public void runInTransaction(Runnable phase) {
        phaseCount++;
        phase.run();
    }

    public int phaseCount() {
        return phaseCount;
    }
}

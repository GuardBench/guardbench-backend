package com.guardbench.testrun.application.port.out;

/**
 * 최종화 직렬화를 위해 TestRun 행 잠금을 획득하는 consumer-owned 아웃바운드 Port다.
 *
 * <p>ADR 0005: 동시 완료 메시지가 같은 {@code RUNNING} 상태와 Quality Gate 부재를 관찰해
 * 같은 평가·Quality Gate 생성을 경합하지 않도록, readiness 확인부터 최종화까지를
 * TestRun 행 잠금으로 직렬화한다.
 */
public interface LockTestRunPort {

    /**
     * 호출자의 트랜잭션 범위에서 TestRun 행에 배타 잠금을 획득한다.
     *
     * <p>잠금은 트랜잭션 종료 시 해제된다. 다른 트랜잭션이 이미 잠금을 보유하면 대기한다.
     *
     * @param testRunId TestRun scalar ID
     * @return 잠금을 획득했으면 true, TestRun이 존재하지 않으면 false
     */
    boolean lockForUpdate(long testRunId);
}

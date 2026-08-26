package com.guardbench.testrun.application.port.out;

/**
 * Worker의 persistence phase를 하나의 트랜잭션으로 실행하는 consumer-owned 아웃바운드 Port다.
 *
 * <p>ADR 0004/0005에 따라 Worker의 상태 전이와 후속 Outbox 저장은 원자적이어야 한다.
 * 반면 Guardrail Provider 호출은 트랜잭션 밖에서 수행해야 하므로,
 * 하나의 메시지 처리에 트랜잭션 경계가 여러 번 필요하다.
 *
 * <p>Application Service가 phase 경계를 명시적으로 선언하고
 * Infrastructure Adapter가 실제 트랜잭션 관리자에 연결한다.
 * 메서드 전체가 하나의 트랜잭션인 경우에는 이 Port 대신
 * 진입 메서드에 트랜잭션 경계를 선언한다.
 */
public interface TransactionalPhasePort {

    /**
     * 전달된 phase를 하나의 트랜잭션에서 실행한다.
     *
     * <p>phase 안에서 예외가 발생하면 해당 phase의 모든 쓰기를 rollback한다.
     */
    void runInTransaction(Runnable phase);
}

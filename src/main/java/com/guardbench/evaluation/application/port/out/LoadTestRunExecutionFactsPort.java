package com.guardbench.evaluation.application.port.out;

import java.util.Optional;

/**
 * TestRun 실행 사실을 조회하는 Evaluation 소유 아웃바운드 Port다.
 *
 * <p>ADR 0006에 따라 TestRun Domain 타입을 직접 사용하지 않고 스칼라 값 계약을 사용한다.
 */
public interface LoadTestRunExecutionFactsPort {

    /**
     * 최종화 직렬화를 위해 대상 TestRun 잠금을 획득한 뒤 실행 사실을 로드한다.
     *
     * <p>ADR 0005: 호출자의 트랜잭션 범위에서 실행되며, 잠금은 트랜잭션 종료 시 해제된다.
     * 동시 완료 메시지는 이 호출에서 대기하므로 readiness 확인, 평가, 최종화가 직렬화된다.
     *
     * @param testRunId TestRun scalar ID
     * @return 실행 사실, TestRun이 존재하지 않으면 empty
     */
    Optional<TestRunExecutionFacts> lockAndLoad(long testRunId);
}

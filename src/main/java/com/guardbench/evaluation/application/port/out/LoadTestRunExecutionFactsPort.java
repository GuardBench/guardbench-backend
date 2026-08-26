package com.guardbench.evaluation.application.port.out;

import java.util.Optional;

/**
 * Evaluation Context가 소유하는 outbound Port다.
 * TestRun의 실행 사실을 Evaluation 입력으로 조회한다.
 *
 * <p>ADR 0006에 따라 TestRun Domain 타입을 직접 사용하지 않고
 * 스칼라 값 기반의 consumer-owned 계약을 사용한다.
 */
public interface LoadTestRunExecutionFactsPort {

    /**
     * 지정된 TestRun의 실행 사실을 조회한다.
     * TestRun이 존재하지 않거나 실행 데이터가 불충분하면 empty를 반환한다.
     */
    Optional<TestRunExecutionFacts> load(long testRunId);
}

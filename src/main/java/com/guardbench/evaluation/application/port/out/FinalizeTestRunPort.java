package com.guardbench.evaluation.application.port.out;

/**
 * Evaluation Context가 소유하는 outbound Port다.
 * TestRun을 FINISHED 상태로 전환한다.
 *
 * <p>ADR 0004에 따라 QualityGateResult 저장과 TestRun FINISHED 전환은
 * 같은 PostgreSQL 트랜잭션에서 호출되어야 하며, 이 Port의 구현은
 * 같은 트랜잭션 참여가 보장되어야 한다.
 *
 * <p>ADR 0006에 따라 TestRun Domain 타입을 직접 사용하지 않고 스칼라 값 기반이다.
 */
public interface FinalizeTestRunPort {

    /**
     * TestRun을 FINISHED 상태로 전환한다.
     *
     * @param testRunId TestRun scalar ID
     * @param executionOutcomeCode TestRunExecutionOutcome code (COMPLETED, INCOMPLETE, ERROR)
     * @param processedTestCaseCount 처리된 TestCase 수
     * @param testCaseCount 전체 TestCase 수
     * @throws IllegalStateException TestRun이 FINISHED 가능 상태가 아닌 경우
     */
    void finalize(long testRunId, String executionOutcomeCode, int processedTestCaseCount, int testCaseCount);
}

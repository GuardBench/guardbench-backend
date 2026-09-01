package com.guardbench.evaluation.application.port.out;

/**
 * TestRun을 최종화하는 Evaluation 소유 아웃바운드 Port다.
 *
 * <p>ADR 0006에 따라 TestRun Domain 타입을 직접 사용하지 않고 스칼라 값 계약을 사용한다.
 */
public interface FinalizeTestRunPort {

    /**
     * TestRun을 FINISHED 상태로 전환한다.
     *
     * @param testRunId TestRun scalar ID
     * @param executionOutcomeCode TestRunExecutionOutcome code (COMPLETED, INCOMPLETE, ERROR)
     * @param processedTestCaseCount 처리된 TestCase 수
     * @param testCaseCount 전체 TestCase 수
     */
    void finalize(long testRunId, String executionOutcomeCode, int processedTestCaseCount, int testCaseCount);

    /**
     * TestRun이 아직 RUNNING인 부분 완료 시점에 절대 진행도를 갱신한다.
     *
     * <p>ADR 0005 4단계: 모든 실행이 terminal이 아니어도 목록·상세 조회의
     * 진행률 계약을 만족시키기 위해 처리 완료된 실행 수를 갱신해야 한다.
     * 호출자의 최종화 직렬화 트랜잭션 범위에서 실행되어야 한다.
     *
     * @param testRunId TestRun scalar ID
     */
    void updateProgress(long testRunId);
}

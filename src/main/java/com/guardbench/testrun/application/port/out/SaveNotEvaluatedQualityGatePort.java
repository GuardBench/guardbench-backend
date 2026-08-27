package com.guardbench.testrun.application.port.out;

/**
 * TestRun이 소유하는 outbound Port다.
 * 대상 준비 실패 시 NOT_EVALUATED QualityGateResult를 생성한다.
 *
 * <p>ADR 0004에 따라 QualityGateResult 저장과 TestRun FINISHED/ERROR 전환은
 * 같은 PostgreSQL 트랜잭션에서 원자적으로 수행되어야 한다.
 *
 * <p>ADR 0006에 따라 이 Port를 소비하는 testrun application은
 * evaluation domain 타입을 직접 import하지 않는다.
 * Integration Adapter가 evaluation context와 연결한다.
 */
public interface SaveNotEvaluatedQualityGatePort {

    /**
     * 지정된 TestRun에 대해 NOT_EVALUATED QualityGateResult를 저장한다.
     * 이미 존재하면 무시한다(멱등).
     *
     * @param testRunId TestRun scalar ID
     */
    void saveNotEvaluated(long testRunId);
}

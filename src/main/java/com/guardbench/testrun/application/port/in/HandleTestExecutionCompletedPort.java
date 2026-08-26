package com.guardbench.testrun.application.port.in;

/**
 * TestExecutionCompleted 메시지 처리를 위한 inbound Port다.
 *
 * <p>SQS 폴링 어댑터가 TestExecutionCompleted 메시지를 수신하면
 * 이 Port를 호출한다. 구현체는 evaluation Context의 FinalizeTestRunService를
 * Integration Adapter를 통해 호출한다.
 *
 * <p>ADR 0006에 따라 testrun infrastructure(messaging)가 evaluation domain을
 * 직접 import하지 않도록 이 Port를 경계로 사용한다.
 */
public interface HandleTestExecutionCompletedPort {

    /**
     * TestExecutionCompleted 메시지를 처리한다.
     *
     * @param testRunId TestRun scalar ID
     * @return ack해야 하면 true, nack(재전달)이면 false
     */
    boolean handle(long testRunId);
}

package com.guardbench.testrun.application.port.out;

/**
 * 고객 AI 서비스의 HTTP endpoint로 하나의 Snapshot input을 실행하는 소비자 소유 Port다.
 */
public interface AiServiceExecutionPort {

    AiServiceExecutionResult execute(AiServiceExecutionRequest request);
}

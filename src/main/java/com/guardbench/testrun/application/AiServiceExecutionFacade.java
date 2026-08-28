package com.guardbench.testrun.application;

import java.util.Objects;

import com.guardbench.testrun.application.port.out.AiServiceExecutionPort;
import com.guardbench.testrun.application.port.out.AiServiceExecutionRequest;

/**
 * 고객 AI 서비스 실행을 다른 bounded context에 노출하는 TestRun Application Facade다.
 *
 * <p>외부 호출자는 TestRun Domain 타입이나 HTTP Adapter를 직접 알지 않고 endpoint와 input만 전달한다.
 */
public final class AiServiceExecutionFacade {

    private final AiServiceExecutionPort executionPort;

    public AiServiceExecutionFacade(AiServiceExecutionPort executionPort) {
        this.executionPort = Objects.requireNonNull(executionPort, "executionPort must not be null");
    }

    public String execute(String endpoint, String input) {
        return executionPort.execute(new AiServiceExecutionRequest(endpoint, input)).actionCode();
    }
}

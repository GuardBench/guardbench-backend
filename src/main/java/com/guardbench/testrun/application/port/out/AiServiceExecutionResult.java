package com.guardbench.testrun.application.port.out;

/**
 * HTTP Adapter가 고객 AI 서비스의 명시적 action을 옮겨 담은 실행 결과다.
 */
public record AiServiceExecutionResult(String actionCode) {

    public AiServiceExecutionResult {
        if (!"ALLOW".equals(actionCode) && !"BLOCK".equals(actionCode)) {
            throw new IllegalArgumentException("action code must be ALLOW or BLOCK");
        }
    }
}

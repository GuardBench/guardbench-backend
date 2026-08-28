package com.guardbench.testrun.application.port.out;

/**
 * 고객 AI 서비스 HTTP 실행에 필요한 요청 값이다.
 */
public record AiServiceExecutionRequest(String endpoint, String input) {

    public AiServiceExecutionRequest {
        validateText(endpoint, "endpoint");
        validateText(input, "input");
    }

    private static void validateText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

package com.guardbench.common.error;

/**
 * MVP Application Error의 전체 계약이다.
 *
 * <p>Code는 클라이언트 분기와 자동화 테스트가 사용하는 안정적인 값이며, {@code message}는 사용자 안내용이다.
 * 새 Code 추가나 기존 의미 변경은 공개 계약 변경으로 다룬다.
 *
 * <p>HTTP Status를 Spring 타입이 아닌 {@code int}로 보관해 Application·Domain 계층이 이 enum을 사용해도
 * Spring MVC 타입에 의존하지 않게 한다.
 */
public enum ApplicationErrorCode {

    VALIDATION_ERROR(400, "요청 값이 올바르지 않습니다."),
    TEST_SUITE_NOT_FOUND(404, "TestSuite를 찾을 수 없습니다."),
    TEST_CASE_NOT_FOUND(404, "TestCase를 찾을 수 없습니다."),
    TEST_RUN_NOT_FOUND(404, "TestRun을 찾을 수 없습니다."),
    TEST_SUITE_EMPTY(409, "실행 가능한 TestCase가 없습니다."),
    IDEMPOTENCY_KEY_CONFLICT(409, "Idempotency-Key가 다른 요청에 이미 사용되었습니다."),
    TEST_RUN_NOT_FINISHED(409, "TestRun이 아직 종료되지 않았습니다."),
    TEST_RUNS_NOT_COMPARABLE(409, "두 TestRun은 비교할 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int httpStatus;
    private final String defaultMessage;

    ApplicationErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 응답 {@code data.code}에 사용하는 안정적인 값이다.
     */
    public String code() {
        return name();
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

package com.guardbench.common.error;

/**
 * 요청 계약으로 표현되는 실패를 나타낸다.
 *
 * <p>Presentation 계층의 전역 예외 처리기가 {@link ApplicationErrorCode}에 정의된 HTTP Status와
 * {@code data.code}로 변환한다. 내부 예외와 Stack Trace는 응답에 노출하지 않는다.
 */
public class ApplicationException extends RuntimeException {

    private final ApplicationErrorCode errorCode;

    public ApplicationException(ApplicationErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    /**
     * 기본 message 대신 사용자 안내 문구를 다르게 표현할 때 사용한다. Code의 의미는 변경하지 않는다.
     */
    public ApplicationException(ApplicationErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApplicationException(ApplicationErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ApplicationErrorCode errorCode() {
        return errorCode;
    }
}

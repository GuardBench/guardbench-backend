package com.guardbench.testrun.application.messaging;

/** 필수 v1 필드, event type 또는 schema version이 유효하지 않은 메시지다. */
public final class InvalidTestRunMessageException extends RuntimeException {

    public InvalidTestRunMessageException(String message) {
        super(message);
    }

    public InvalidTestRunMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

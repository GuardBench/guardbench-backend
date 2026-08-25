package com.guardbench.testrun.application.port.out;

/**
 * AWS 예외와 원문 메시지를 Port 경계에서 숨기기 위한 안정적인 failure code다.
 */
public enum GuardrailFailureCode {
    TARGET_NOT_FOUND,
    TARGET_ACCESS_DENIED,
    TARGET_CONFIGURATION_INVALID,
    PROVIDER_UNAVAILABLE,
    PROVIDER_RESPONSE_INVALID,
    PROVIDER_TIMEOUT
}

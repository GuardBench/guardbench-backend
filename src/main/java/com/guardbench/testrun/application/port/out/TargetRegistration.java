package com.guardbench.testrun.application.port.out;

/**
 * Target 경계에 실행 대상 등록을 요청하는 provider-independent 값 계약이다.
 *
 * <p>TestRun은 각 값의 provider 의미를 해석하지 않는다.
 */
public record TargetRegistration(String typeCode, String identifier, String revision) {

    public TargetRegistration {
        requireNonBlank(typeCode, "target type code");
        requireNonBlank(identifier, "target identifier");
        requireNonBlank(revision, "target revision");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

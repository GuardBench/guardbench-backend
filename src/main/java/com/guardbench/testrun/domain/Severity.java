package com.guardbench.testrun.domain;

public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW;

    public static Severity fromCode(String code) {
        return Severity.valueOf(code);
    }
}

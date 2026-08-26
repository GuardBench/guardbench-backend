package com.guardbench.testdefinition.presentation.dto;

import com.guardbench.testdefinition.domain.Severity;

public enum SeverityApiValue {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public Severity toDomain() {
        return Severity.valueOf(name());
    }
}

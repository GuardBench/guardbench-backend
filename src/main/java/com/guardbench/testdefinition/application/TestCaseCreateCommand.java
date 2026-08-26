package com.guardbench.testdefinition.application;

import java.util.Objects;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;

public record TestCaseCreateCommand(
        String name,
        String input,
        Action expectedAction,
        Severity severity,
        String category) {

    public TestCaseCreateCommand {
        Objects.requireNonNull(expectedAction, "expectedAction must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
    }
}

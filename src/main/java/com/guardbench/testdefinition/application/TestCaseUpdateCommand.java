package com.guardbench.testdefinition.application;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;

public record TestCaseUpdateCommand(
        boolean namePresent,
        String name,
        boolean inputPresent,
        String input,
        boolean expectedActionPresent,
        Action expectedAction,
        boolean severityPresent,
        Severity severity,
        boolean categoryPresent,
        String category) {

    public TestCaseUpdateCommand {
        if (!namePresent && !inputPresent && !expectedActionPresent
                && !severityPresent && !categoryPresent) {
            throw new IllegalArgumentException("수정할 값이 최소 하나 필요합니다.");
        }
    }
}

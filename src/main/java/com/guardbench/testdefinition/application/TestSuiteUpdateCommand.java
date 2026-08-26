package com.guardbench.testdefinition.application;

public record TestSuiteUpdateCommand(
        boolean namePresent,
        String name,
        boolean descriptionPresent,
        String description) {

    public TestSuiteUpdateCommand {
        if (!namePresent && !descriptionPresent) {
            throw new IllegalArgumentException("수정할 값이 최소 하나 필요합니다.");
        }
        if (namePresent && name == null) {
            throw new IllegalArgumentException("TestSuite 이름은 null일 수 없습니다.");
        }
    }
}

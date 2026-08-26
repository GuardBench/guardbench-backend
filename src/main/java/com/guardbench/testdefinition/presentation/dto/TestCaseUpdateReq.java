package com.guardbench.testdefinition.presentation.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

import com.guardbench.testdefinition.application.TestCaseUpdateCommand;
import com.guardbench.testdefinition.presentation.validation.ValidTestCaseUpdate;

@ValidTestCaseUpdate
public final class TestCaseUpdateReq {

    private String name;
    private String input;
    private ActionApiValue expectedAction;
    private SeverityApiValue severity;
    private String category;
    private boolean namePresent;
    private boolean inputPresent;
    private boolean expectedActionPresent;
    private boolean severityPresent;
    private boolean categoryPresent;

    @JsonSetter("name")
    public void setName(String name) { this.name = name; this.namePresent = true; }

    @JsonSetter("input")
    public void setInput(String input) { this.input = input; this.inputPresent = true; }

    @JsonSetter("expectedAction")
    public void setExpectedAction(ActionApiValue expectedAction) {
        this.expectedAction = expectedAction;
        this.expectedActionPresent = true;
    }

    @JsonSetter("severity")
    public void setSeverity(SeverityApiValue severity) {
        this.severity = severity;
        this.severityPresent = true;
    }

    @JsonSetter("category")
    public void setCategory(String category) { this.category = category; this.categoryPresent = true; }

    public String name() { return name; }
    public String input() { return input; }
    public ActionApiValue expectedAction() { return expectedAction; }
    public SeverityApiValue severity() { return severity; }
    public String category() { return category; }
    public boolean namePresent() { return namePresent; }
    public boolean inputPresent() { return inputPresent; }
    public boolean expectedActionPresent() { return expectedActionPresent; }
    public boolean severityPresent() { return severityPresent; }
    public boolean categoryPresent() { return categoryPresent; }

    public boolean hasAnyField() {
        return namePresent || inputPresent || expectedActionPresent || severityPresent || categoryPresent;
    }

    public TestCaseUpdateCommand toCommand() {
        return new TestCaseUpdateCommand(
                namePresent, name,
                inputPresent, input,
                expectedActionPresent,
                expectedAction == null ? null : expectedAction.toDomain(),
                severityPresent,
                severity == null ? null : severity.toDomain(),
                categoryPresent, category);
    }
}

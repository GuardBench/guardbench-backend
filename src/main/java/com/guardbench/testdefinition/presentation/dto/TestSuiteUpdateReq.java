package com.guardbench.testdefinition.presentation.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

import com.guardbench.testdefinition.application.TestSuiteUpdateCommand;
import com.guardbench.testdefinition.presentation.validation.ValidTestSuiteUpdate;

@ValidTestSuiteUpdate
public final class TestSuiteUpdateReq {

    private String name;
    private String description;
    private boolean namePresent;
    private boolean descriptionPresent;

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean namePresent() {
        return namePresent;
    }

    public boolean descriptionPresent() {
        return descriptionPresent;
    }

    public TestSuiteUpdateCommand toCommand() {
        return new TestSuiteUpdateCommand(
                namePresent, name, descriptionPresent, description);
    }
}

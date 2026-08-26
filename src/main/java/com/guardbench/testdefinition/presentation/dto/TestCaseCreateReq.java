package com.guardbench.testdefinition.presentation.dto;

import jakarta.validation.constraints.NotNull;

import com.guardbench.testdefinition.application.TestCaseCreateCommand;
import com.guardbench.testdefinition.presentation.validation.ContractNotBlank;

public record TestCaseCreateReq(
        @NotNull(message = "이름은 필수입니다.")
        @ContractNotBlank(message = "이름은 필수입니다.") String name,
        @NotNull(message = "입력은 필수입니다.")
        @ContractNotBlank(message = "입력은 필수입니다.") String input,
        @NotNull(message = "기대 Action은 필수입니다.") ActionApiValue expectedAction,
        @NotNull(message = "Severity는 필수입니다.") SeverityApiValue severity,
        @NotNull(message = "category는 필수입니다.")
        @ContractNotBlank(message = "category는 필수입니다.") String category) {

    public TestCaseCreateCommand toCommand() {
        return new TestCaseCreateCommand(
                name,
                input,
                expectedAction.toDomain(),
                severity.toDomain(),
                category);
    }
}

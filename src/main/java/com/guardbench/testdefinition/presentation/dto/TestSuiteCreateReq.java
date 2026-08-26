package com.guardbench.testdefinition.presentation.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.guardbench.testdefinition.application.TestCaseCreateCommand;
import com.guardbench.testdefinition.application.TestSuiteCreateCommand;
import com.guardbench.testdefinition.presentation.validation.ContractNotBlank;

public record TestSuiteCreateReq(
        @NotNull(message = "이름은 필수입니다.")
        @ContractNotBlank(message = "이름은 필수입니다.") String name,
        String description,
        @Size(max = 100, message = "초기 TestCase는 최대 100개까지 허용합니다.")
        List<@NotNull(message = "TestCase는 null일 수 없습니다.")
                @Valid TestCaseCreateReq> testCases) {

    public TestSuiteCreateCommand toCommand() {
        List<TestCaseCreateCommand> commands = testCases == null
                ? List.of()
                : testCases.stream().map(TestCaseCreateReq::toCommand).toList();
        return new TestSuiteCreateCommand(name, description, commands);
    }
}

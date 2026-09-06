package com.guardbench.testdefinition.presentation.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.guardbench.testdefinition.application.TestCaseBulkCreateCommand;

public record TestCaseBulkCreateReq(
        @NotNull(message = "items는 필수입니다.")
        @Size(min = 1, max = 1000, message = "TestCase는 1개 이상 1000개 이하로 등록해야 합니다.")
        List<@NotNull(message = "TestCase는 null일 수 없습니다.") @Valid TestCaseCreateReq> items) {

    public TestCaseBulkCreateCommand toCommand(String idempotencyKey) {
        return new TestCaseBulkCreateCommand(
                idempotencyKey,
                items.stream().map(TestCaseCreateReq::toCommand).toList());
    }
}

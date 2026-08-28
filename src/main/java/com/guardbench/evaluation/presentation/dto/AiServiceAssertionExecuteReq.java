package com.guardbench.evaluation.presentation.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.guardbench.evaluation.application.ExecuteAiServiceAssertionService;
import com.guardbench.evaluation.domain.EvaluationAction;

public record AiServiceAssertionExecuteReq(
        @NotEmpty List<@Valid CaseReq> cases
) {
    public List<ExecuteAiServiceAssertionService.Case> toCases() {
        return cases.stream()
                .map(item -> new ExecuteAiServiceAssertionService.Case(item.input(), item.expectedAction()))
                .toList();
    }

    public record CaseReq(
            @NotBlank String input,
            @NotNull EvaluationAction expectedAction
    ) {
    }
}

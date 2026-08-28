package com.guardbench.evaluation.presentation.dto;

import java.util.List;

import com.guardbench.evaluation.application.ExecuteAiServiceAssertionService;
import com.guardbench.evaluation.domain.AssertionStatus;
import com.guardbench.evaluation.domain.EvaluationAction;

public record AiServiceAssertionExecuteRes(List<ResultItem> results) {

    public static AiServiceAssertionExecuteRes from(List<ExecuteAiServiceAssertionService.Result> results) {
        return new AiServiceAssertionExecuteRes(results.stream().map(ResultItem::from).toList());
    }

    public record ResultItem(
            String input,
            EvaluationAction expectedAction,
            EvaluationAction actualAction,
            AssertionStatus assertionStatus
    ) {
        private static ResultItem from(ExecuteAiServiceAssertionService.Result result) {
            return new ResultItem(
                    result.input(),
                    result.expectedAction(),
                    result.actualAction(),
                    result.assertionResult().status()
            );
        }
    }
}

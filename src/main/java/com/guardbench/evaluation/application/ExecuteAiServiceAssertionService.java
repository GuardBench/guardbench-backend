package com.guardbench.evaluation.application;

import java.util.List;
import java.util.Objects;

import com.guardbench.evaluation.application.port.out.AiServiceActionPort;
import com.guardbench.evaluation.domain.AssertionResult;
import com.guardbench.evaluation.domain.AssertionStatus;
import com.guardbench.evaluation.domain.EvaluationAction;

/**
 * 단일 고객 AI endpoint에 테스트 케이스들을 실행하고 Assertion만 계산하는 MVP 전용 Use Case다.
 *
 * <p>Baseline, Comparability, Change Classification, Quality Gate는 이 흐름에서 수행하지 않는다.
 */
public final class ExecuteAiServiceAssertionService {

    private final AiServiceActionPort actionPort;

    public ExecuteAiServiceAssertionService(AiServiceActionPort actionPort) {
        this.actionPort = Objects.requireNonNull(actionPort, "actionPort must not be null");
    }

    public List<Result> execute(String endpoint, List<Case> cases) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        Objects.requireNonNull(cases, "cases must not be null");

        return cases.stream()
                .map(testCase -> executeCase(endpoint, testCase))
                .toList();
    }

    private Result executeCase(String endpoint, Case testCase) {
        Objects.requireNonNull(testCase, "test case must not be null");
        EvaluationAction actualAction = actionPort.execute(endpoint, testCase.input());
        AssertionResult assertionResult = new AssertionResult(
                testCase.expectedAction() == actualAction ? AssertionStatus.PASS : AssertionStatus.FAIL
        );
        return new Result(testCase.input(), testCase.expectedAction(), actualAction, assertionResult);
    }

    public record Case(String input, EvaluationAction expectedAction) {
        public Case {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("input must not be blank");
            }
            Objects.requireNonNull(expectedAction, "expectedAction must not be null");
        }
    }

    public record Result(
            String input,
            EvaluationAction expectedAction,
            EvaluationAction actualAction,
            AssertionResult assertionResult
    ) {
    }
}

package com.guardbench.testrun.application;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;
import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.EvaluationResult;
import com.guardbench.testrun.domain.TestExecutionError;
import com.guardbench.testrun.domain.TestExecutionErrorCode;
import com.guardbench.testrun.domain.TestExecutionErrorStage;

/** Evaluator Port의 provider-independent 결과를 TestRun local 계약으로 변환한다. */
public final class EvaluatorResultNormalizer {

    private EvaluatorResultNormalizer() {
    }

    public static EvaluatorExecutionNormalization normalize(EvaluatorExecutionResult providerResult) {
        if (providerResult == null) {
            return failed(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID);
        }
        if (!providerResult.isSuccess()) {
            return failed(providerResult.failureCode());
        }

        try {
            return EvaluatorExecutionNormalization.succeeded(
                    new EvaluationResult(Action.fromCode(providerResult.actionCode())));
        } catch (IllegalArgumentException exception) {
            return failed(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID);
        }
    }

    private static EvaluatorExecutionNormalization failed(EvaluatorFailureCode failureCode) {
        TestExecutionError error = switch (failureCode) {
            case EVALUATOR_NOT_FOUND -> new TestExecutionError(
                    TestExecutionErrorStage.EVALUATOR,
                    TestExecutionErrorCode.EVALUATOR_NOT_FOUND,
                    "Evaluator was not found.");
            case EVALUATOR_ACCESS_DENIED -> new TestExecutionError(
                    TestExecutionErrorStage.EVALUATOR,
                    TestExecutionErrorCode.EVALUATOR_ACCESS_DENIED,
                    "Evaluator access was denied.");
            case EVALUATOR_CONFIGURATION_INVALID -> new TestExecutionError(
                    TestExecutionErrorStage.EVALUATOR,
                    TestExecutionErrorCode.EVALUATOR_CONFIGURATION_INVALID,
                    "Evaluator configuration is invalid.");
            case PROVIDER_UNAVAILABLE -> new TestExecutionError(
                    TestExecutionErrorStage.EVALUATOR,
                    TestExecutionErrorCode.PROVIDER_UNAVAILABLE,
                    "Evaluator provider is unavailable.");
            case PROVIDER_RESPONSE_INVALID -> new TestExecutionError(
                    TestExecutionErrorStage.EVALUATOR,
                    TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID,
                    "Evaluator provider response is invalid.");
            case PROVIDER_TIMEOUT -> new TestExecutionError(
                    TestExecutionErrorStage.EVALUATOR,
                    TestExecutionErrorCode.PROVIDER_TIMEOUT,
                    "Evaluator provider timed out.");
        };
        return EvaluatorExecutionNormalization.failed(error);
    }
}

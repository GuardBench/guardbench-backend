package com.guardbench.testrun.application.port.out;

import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.TestExecutionStatus;

public enum TestRunResultAttentionType {
    FALSE_NEGATIVE,
    FALSE_POSITIVE,
    EXECUTION_FAILED,
    TIMED_OUT,
    NOT_STARTED;

    public static TestRunResultAttentionType classify(
            TestExecutionStatus executionStatus,
            Action expectedAction,
            Action evaluatorVerdict) {
        return switch (executionStatus) {
            case FAILED -> EXECUTION_FAILED;
            case TIMED_OUT -> TIMED_OUT;
            case NOT_STARTED -> NOT_STARTED;
            case SUCCEEDED -> classifyEvaluation(expectedAction, evaluatorVerdict);
        };
    }

    private static TestRunResultAttentionType classifyEvaluation(
            Action expectedAction, Action evaluatorVerdict) {
        if (expectedAction == Action.BLOCK && evaluatorVerdict == Action.ALLOW) {
            return FALSE_NEGATIVE;
        }
        if (expectedAction == Action.ALLOW && evaluatorVerdict == Action.BLOCK) {
            return FALSE_POSITIVE;
        }
        return null;
    }
}

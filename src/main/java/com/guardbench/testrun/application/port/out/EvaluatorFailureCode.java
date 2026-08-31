package com.guardbench.testrun.application.port.out;

/** Evaluator provider 오류의 안전한 분류다. provider 원문은 Port 밖으로 전달하지 않는다. */
public enum EvaluatorFailureCode {
    EVALUATOR_NOT_FOUND,
    EVALUATOR_ACCESS_DENIED,
    EVALUATOR_CONFIGURATION_INVALID,
    PROVIDER_UNAVAILABLE,
    PROVIDER_RESPONSE_INVALID,
    PROVIDER_TIMEOUT
}

package com.guardbench.testrun.application.port.out;

/** Application response를 공통 EvaluationResult로 정규화하는 consumer-owned Port다. */
public interface EvaluatorExecutionPort {

    EvaluatorExecutionResult evaluate(EvaluatorExecutionRequest request);
}

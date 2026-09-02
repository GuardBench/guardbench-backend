package com.guardbench.testrun.infrastructure.messaging.reliability;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionPort;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionRequest;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;

/**
 * Issue #153: reliability integration test에서 Evaluator 호출을 항상 {@code ALLOW}로
 * 고정하는 Stub이다. 이 test suite의 관심사는 Target/claim/SQS 결합 동작이므로
 * Evaluator 경로는 결정적으로 성공시켜 관측 대상에서 제외한다.
 */
public final class AlwaysAllowEvaluatorExecutionStub implements EvaluatorExecutionPort {

    @Override
    public EvaluatorExecutionResult evaluate(EvaluatorExecutionRequest request) {
        return EvaluatorExecutionResult.succeeded("ALLOW");
    }
}

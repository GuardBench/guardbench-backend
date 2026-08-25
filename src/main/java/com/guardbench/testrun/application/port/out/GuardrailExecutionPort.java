package com.guardbench.testrun.application.port.out;

/**
 * 고정된 Guardrail version으로 하나의 Snapshot input을 평가하는 소비자 소유 Port다.
 */
public interface GuardrailExecutionPort {

    GuardrailExecutionResult execute(GuardrailExecutionRequest request);
}

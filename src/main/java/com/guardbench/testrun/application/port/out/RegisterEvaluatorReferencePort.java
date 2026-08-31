package com.guardbench.testrun.application.port.out;

import com.guardbench.testrun.domain.EvaluatorReference;

/** TestRun에 고정할 Evaluator의 provider 설정과 numbered revision을 저장한다. */
public interface RegisterEvaluatorReferencePort {
    void register(EvaluatorReference reference, EvaluatorRegistration registration);
}

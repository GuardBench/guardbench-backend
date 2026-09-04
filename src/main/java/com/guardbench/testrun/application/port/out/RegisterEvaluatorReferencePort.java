package com.guardbench.testrun.application.port.out;

import com.guardbench.testrun.domain.EvaluatorReference;

/** TestRun에 고정할 Response Behavior Classifier의 provider/model 식별자를 저장한다. */
public interface RegisterEvaluatorReferencePort {
    void register(EvaluatorReference reference, EvaluatorRegistration registration);
}

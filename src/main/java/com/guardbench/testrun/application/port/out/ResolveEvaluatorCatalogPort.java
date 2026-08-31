package com.guardbench.testrun.application.port.out;

import java.util.Optional;

import com.guardbench.testrun.domain.EvaluationProfile;

/** 운영자가 구성한 catalog에서 profile에 대응하는 실제 Evaluator를 찾는다. */
public interface ResolveEvaluatorCatalogPort {
    Optional<EvaluatorRegistration> resolve(EvaluationProfile profile);
}

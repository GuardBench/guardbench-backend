package com.guardbench.testrun.infrastructure.evaluator;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.guardbench.testrun.application.port.out.EvaluatorRegistration;
import com.guardbench.testrun.application.port.out.ResolveEvaluatorCatalogPort;
import com.guardbench.testrun.domain.EvaluationProfile;

/** Profile을 운영자가 사전 구성한 immutable Bedrock Guardrail revision으로 해석한다. */
@Component
class EvaluatorCatalogPersistenceAdapter implements ResolveEvaluatorCatalogPort {
    private static final String BEDROCK_GUARDRAIL = "BEDROCK_GUARDRAIL";
    private final EvaluatorCatalogProperties properties;

    EvaluatorCatalogPersistenceAdapter(EvaluatorCatalogProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<EvaluatorRegistration> resolve(EvaluationProfile profile) {
        return properties.entries().stream()
                .filter(entry -> sameProfile(entry, profile))
                .map(entry -> new EvaluatorRegistration(BEDROCK_GUARDRAIL,
                        entry.guardrailIdentifier(), entry.guardrailRevision()))
                .findFirst();
    }

    private static boolean sameProfile(EvaluatorCatalogProperties.Entry entry, EvaluationProfile profile) {
        try {
            return new EvaluationProfile(entry.checks(), entry.strictness()).equals(profile);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}

package com.guardbench.testrun.infrastructure.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import com.guardbench.testrun.application.port.out.EvaluatorRegistration;
import com.guardbench.testrun.domain.EvaluationProfile;

class EvaluatorCatalogConfigurationTest {

    @Test
    void defaultApplicationCatalogContainsAllNineteenCanonicalEntries() throws Exception {
        EvaluatorCatalogProperties properties = properties();

        assertEquals(19, properties.entries().size());
        assertTrue(properties.entries().stream().allMatch(entry ->
                entry.guardrailIdentifier() != null && !entry.guardrailIdentifier().isBlank()
                        && entry.guardrailRevision().matches("[1-9][0-9]{0,7}")));
    }

    @Test
    void piiOnlyProfilesCollapseStrictnessToOneEvaluatorReference() throws Exception {
        EvaluatorCatalogPersistenceAdapter adapter = new EvaluatorCatalogPersistenceAdapter(properties());

        EvaluatorRegistration relaxed = adapter.resolve(new EvaluationProfile(List.of("PII_LEAKAGE"), "RELAXED"))
                .orElseThrow();
        EvaluatorRegistration standard = adapter.resolve(new EvaluationProfile(List.of("PII_LEAKAGE"), "STANDARD"))
                .orElseThrow();
        EvaluatorRegistration strict = adapter.resolve(new EvaluationProfile(List.of("PII_LEAKAGE"), "STRICT"))
                .orElseThrow();

        assertEquals(standard, relaxed);
        assertEquals(standard, strict);
    }

    @Test
    void everyCanonicalProfileResolvesFromDefaultCatalog() throws Exception {
        EvaluatorCatalogPersistenceAdapter adapter = new EvaluatorCatalogPersistenceAdapter(properties());
        List<List<String>> checkSets = List.of(
                List.of("PROMPT_INJECTION"),
                List.of("PII_LEAKAGE"),
                List.of("HARMFUL_CONTENT"),
                List.of("PROMPT_INJECTION", "PII_LEAKAGE"),
                List.of("PROMPT_INJECTION", "HARMFUL_CONTENT"),
                List.of("PII_LEAKAGE", "HARMFUL_CONTENT"),
                List.of("PROMPT_INJECTION", "PII_LEAKAGE", "HARMFUL_CONTENT"));

        for (List<String> checks : checkSets) {
            for (String strictness : List.of("RELAXED", "STANDARD", "STRICT")) {
                assertTrue(adapter.resolve(new EvaluationProfile(checks, strictness)).isPresent(),
                        () -> "missing catalog entry for " + checks + "/" + strictness);
            }
        }
    }

    private static EvaluatorCatalogProperties properties() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application", new ClassPathResource("application.yml"));
        sources.forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment)
                .bind("guardbench.evaluator-catalog", Bindable.of(EvaluatorCatalogProperties.class))
                .orElseThrow(IllegalStateException::new);
    }
}

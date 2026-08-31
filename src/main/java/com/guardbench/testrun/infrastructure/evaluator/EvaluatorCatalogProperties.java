package com.guardbench.testrun.infrastructure.evaluator;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 운영자가 사전 구성한 Bedrock Guardrail Evaluator catalog다. */
@ConfigurationProperties(prefix = "guardbench.evaluator-catalog")
public record EvaluatorCatalogProperties(List<Entry> entries) {
    public EvaluatorCatalogProperties {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(List<String> checks, String strictness, String guardrailIdentifier, String guardrailRevision) {
        public Entry {
            checks = checks == null ? List.of() : List.copyOf(checks);
        }
    }
}

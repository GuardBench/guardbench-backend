package com.guardbench.testrun.infrastructure.evaluator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 운영자가 사전 구성한 Bedrock Guardrail Evaluator catalog다. */
@ConfigurationProperties(prefix = "guardbench.evaluator-catalog")
public record EvaluatorCatalogProperties(List<Entry> entries) {
    public EvaluatorCatalogProperties {
        entries = entries == null ? List.of() : List.copyOf(entries);
        Set<String> keys = new HashSet<>();
        for (Entry entry : entries) {
            if (!keys.add(canonicalKey(entry.checks(), entry.strictness()))) {
                throw new IllegalArgumentException("evaluator catalog contains duplicate canonical profile");
            }
        }
    }

    private static String canonicalKey(List<String> checks, String strictness) {
        List<String> canonicalChecks = checks.stream().sorted().toList();
        String canonicalStrictness = canonicalChecks.equals(List.of("PII_LEAKAGE")) ? "STANDARD" : strictness;
        return String.join(",", canonicalChecks) + ":" + canonicalStrictness;
    }

    public record Entry(List<String> checks, String strictness, String guardrailIdentifier, String guardrailRevision) {
        public Entry {
            checks = checks == null ? List.of() : List.copyOf(checks);
            if (checks.isEmpty() || checks.stream().anyMatch(check -> check == null || check.isBlank())
                    || strictness == null || strictness.isBlank()
                    || guardrailIdentifier == null || guardrailIdentifier.isBlank()
                    || guardrailRevision == null || !guardrailRevision.matches("[1-9][0-9]{0,7}")) {
                throw new IllegalArgumentException("evaluator catalog entry is invalid");
            }
        }
    }
}

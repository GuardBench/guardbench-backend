package com.guardbench.testrun.domain;

import java.util.Objects;

public record CandidateTarget(String guardrailId, CandidateSource requestedSource, String resolvedVersion) {

    public CandidateTarget {
        if (guardrailId == null || guardrailId.isBlank()) {
            throw new IllegalArgumentException("guardrail ID must not be blank");
        }
        Objects.requireNonNull(requestedSource, "requested source must not be null");
        if (resolvedVersion != null && (!resolvedVersion.chars().allMatch(Character::isDigit) || resolvedVersion.isBlank())) {
            throw new IllegalArgumentException("resolved version must be numbered");
        }
    }

    public CandidateTarget resolve(String version) {
        if (version == null) {
            throw new IllegalArgumentException("resolved version must not be null");
        }
        return new CandidateTarget(guardrailId, requestedSource, version);
    }
}

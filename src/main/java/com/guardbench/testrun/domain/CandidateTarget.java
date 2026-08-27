package com.guardbench.testrun.domain;

import java.util.Objects;

public record CandidateTarget(String guardrailId, CandidateSource requestedSource, String resolvedVersion) {

    public CandidateTarget {
        if (isContractBlank(guardrailId)) {
            throw new IllegalArgumentException("guardrail ID must not be blank");
        }
        Objects.requireNonNull(requestedSource, "requested source must not be null");
        if (resolvedVersion != null && (!resolvedVersion.chars().allMatch(Character::isDigit) || isContractBlank(resolvedVersion))) {
            throw new IllegalArgumentException("resolved version must be numbered");
        }
    }

    public CandidateTarget resolve(String version) {
        if (version == null) {
            throw new IllegalArgumentException("resolved version must not be null");
        }
        return new CandidateTarget(guardrailId, requestedSource, version);
    }

    private static boolean isContractBlank(String value) {
        return value == null || value.codePoints().allMatch(CandidateTarget::isContractWhitespace);
    }

    private static boolean isContractWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF;
    }
}

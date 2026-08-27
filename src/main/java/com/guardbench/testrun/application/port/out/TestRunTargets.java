package com.guardbench.testrun.application.port.out;

import java.util.Objects;

import com.guardbench.testrun.domain.CandidateSource;

public record TestRunTargets(
        BaselineTargetView baseline,
        CandidateTargetView candidate) {
    public TestRunTargets {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
    }

    public record BaselineTargetView(String guardrailId, String version) {
        public BaselineTargetView {
            requireNonBlank(guardrailId, "baseline guardrailId");
            requireNonBlank(version, "baseline version");
        }
    }

    public record CandidateTargetView(
            String guardrailId,
            CandidateSource requestedSource,
            String resolvedVersion) {
        public CandidateTargetView {
            requireNonBlank(guardrailId, "candidate guardrailId");
            Objects.requireNonNull(requestedSource, "candidate requestedSource must not be null");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

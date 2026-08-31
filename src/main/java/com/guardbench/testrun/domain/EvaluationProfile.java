package com.guardbench.testrun.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** TestRun 접수 시 사용자가 요청한 불변 평가 정책 snapshot이다. */
public record EvaluationProfile(List<String> checks, String strictness) {

    public EvaluationProfile {
        Objects.requireNonNull(checks, "evaluation checks must not be null");
        if (checks.isEmpty() || checks.stream().anyMatch(EvaluationProfile::isBlank)) {
            throw new IllegalArgumentException("evaluation checks must contain non-blank values");
        }
        checks = checks.stream().sorted(Comparator.naturalOrder()).toList();
        if (checks.stream().distinct().count() != checks.size()) {
            throw new IllegalArgumentException("evaluation checks must be unique");
        }
        if (isBlank(strictness)) {
            throw new IllegalArgumentException("evaluation strictness must not be blank");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.guardbench.testrun.presentation.dto;

import java.time.Instant;

import com.guardbench.testrun.application.TestRunCreateResult;
import com.guardbench.testrun.domain.EvaluationProfile;

/** 새 접수 또는 멱등 재전송으로 반환하는 TestRun 요약이다. */
public record TestRunCreateRes(long id, long testSuiteId, String status, int testCaseCount,
                               TargetReferenceRes target, EvaluationProfileRes evaluationProfile, Instant createdAt) {
    public static TestRunCreateRes from(TestRunCreateResult result) {
        return new TestRunCreateRes(result.id(), result.testSuiteId(), result.status(), result.testCaseCount(),
                new TargetReferenceRes(result.target().referenceId(), result.target().type(),
                        result.target().identifier(), result.target().revision(), result.target().model()), toResponse(result.evaluationProfile()), result.createdAt());
    }

    static EvaluationProfileRes toResponse(EvaluationProfile profile) {
        return profile == null ? null : new EvaluationProfileRes(profile.checks(), profile.strictness());
    }
}

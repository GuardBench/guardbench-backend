package com.guardbench.testrun.application;

import java.time.Instant;

import com.guardbench.testrun.application.port.out.TargetReferenceView;
import com.guardbench.testrun.domain.EvaluationProfile;

/** TestRun 접수 응답을 위한 Application 계층 결과 값이다. */
public record TestRunCreateResult(long id, long testSuiteId, String status, int testCaseCount,
                                  TargetReferenceView target, EvaluationProfile evaluationProfile, Instant createdAt) {
}

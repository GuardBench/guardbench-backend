package com.guardbench.testrun.application.port.out;

import java.time.Instant;

import com.guardbench.testrun.domain.EvaluationProfile;
import com.guardbench.testrun.domain.TestRunStatus;

/** Regression 후보 조회에 필요한 TestRun의 consumer-owned projection이다. */
public record TestRunRegressionView(
        long id,
        long testSuiteId,
        TestRunStatus status,
        TargetReferenceView target,
        EvaluationProfile evaluationProfile,
        String evaluatorConfigKey,
        Instant completedAt) {

    public TestRunRegressionView {
        if (id <= 0 || testSuiteId <= 0) {
            throw new IllegalArgumentException("TestRun IDs must be positive");
        }
        if (status == null || target == null) {
            throw new IllegalArgumentException("TestRun status and target must not be null");
        }
    }

    public boolean isFinished() {
        return status == TestRunStatus.FINISHED;
    }

    public boolean hasSameEvaluatorAs(TestRunRegressionView other) {
        return evaluatorConfigKey != null
                && evaluatorConfigKey.equals(other.evaluatorConfigKey());
    }
}

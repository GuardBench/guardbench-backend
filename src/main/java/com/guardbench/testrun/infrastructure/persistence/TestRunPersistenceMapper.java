package com.guardbench.testrun.infrastructure.persistence;

import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.ApplicationResponse;
import com.guardbench.testrun.domain.EvaluationProfile;
import com.guardbench.testrun.domain.EvaluationResult;
import com.guardbench.testrun.domain.EvaluatorReference;
import com.guardbench.testrun.domain.ExpectedResult;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.SourceTestCaseId;
import com.guardbench.testrun.domain.SourceTestSuiteId;
import com.guardbench.testrun.domain.TargetReference;
import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionError;
import com.guardbench.testrun.domain.TestExecutionErrorCode;
import com.guardbench.testrun.domain.TestExecutionErrorStage;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TestExecutionStatus;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.TestRunStatus;
import com.guardbench.testrun.domain.TestRunTimeline;

final class TestRunPersistenceMapper {
    private TestRunPersistenceMapper() {
    }

    static TestRunEntity toEntity(TestRun source) {
        return TestRunEntity.of(
                source.id().value(),
                source.sourceTestSuiteId().value(),
                source.status().name(),
                source.testCaseCount(),
                source.processedTestCaseCount(),
                source.targetReference().value(),
                String.join(",", source.evaluationProfile().checks()),
                source.evaluationProfile().strictness(),
                source.evaluatorReference().value(),
                source.executionOutcome() == null ? null : source.executionOutcome().name(),
                source.timeline().createdAt(),
                source.timeline().startedAt(),
                source.timeline().completedAt(),
                source.timeline().updatedAt()
        );
    }

    static TestRun toDomain(TestRunEntity source) {
        return TestRun.rehydrate(
                new TestRunId(source.id),
                new SourceTestSuiteId(source.testSuiteId),
                new TargetReference(source.targetReferenceId),
                profileOf(source),
                evaluatorReferenceOf(source),
                source.testCaseCount,
                source.processedTestCaseCount,
                TestRunStatus.valueOf(source.status),
                source.executionOutcome == null ? null : TestRunExecutionOutcome.valueOf(source.executionOutcome),
                new TestRunTimeline(source.createdAt, source.startedAt, source.completedAt, source.updatedAt)
        );
    }

    private static EvaluationProfile profileOf(TestRunEntity source) {
        if (source.evaluationChecks == null || source.evaluationStrictness == null
                || source.evaluatorReferenceId == null) {
            throw new IllegalStateException("incomplete TestRun evaluation snapshot");
        }
        return new EvaluationProfile(java.util.List.of(source.evaluationChecks.split(",")), source.evaluationStrictness);
    }

    private static EvaluatorReference evaluatorReferenceOf(TestRunEntity source) {
        if (source.evaluatorReferenceId == null) {
            throw new IllegalStateException("missing TestRun evaluator reference");
        }
        return new EvaluatorReference(source.evaluatorReferenceId);
    }

    static TestCaseSnapshotEntity toEntity(TestCaseSnapshot source) {
        return TestCaseSnapshotEntity.of(
                source.id().value(),
                source.testRunId().value(),
                source.sourceTestCaseId().value(),
                source.name(),
                source.input(),
                source.expectedResult().action().name(),
                source.severity().name(),
                source.category(),
                source.createdAt()
        );
    }

    static TestCaseSnapshot toDomain(TestCaseSnapshotEntity source) {
        return TestCaseSnapshot.of(
                new TestCaseSnapshotId(source.id),
                new TestRunId(source.testRunId),
                new SourceTestCaseId(source.sourceTestCaseId),
                source.name,
                source.input,
                new ExpectedResult(Action.valueOf(source.expectedAction)),
                Severity.valueOf(source.severity),
                source.category,
                source.createdAt
        );
    }

    static TestExecutionEntity toEntity(TestExecution source) {
        return TestExecutionEntity.of(
                source.id().snapshotId().value(),
                source.status().name(),
                source.applicationResponse() == null ? null : source.applicationResponse().value(),
                source.evaluationResult() == null ? null : source.evaluationResult().action().name(),
                source.error() == null ? null : source.error().stage().name(),
                source.error() == null ? null : source.error().code().name(),
                source.error() == null ? null : source.error().message(),
                source.startedAt(),
                source.completedAt()
        );
    }

    static TestExecution toDomain(TestExecutionEntity source) {
        TestExecutionId id = new TestExecutionId(new TestCaseSnapshotId(source.snapshotId));
        TestExecutionStatus status = TestExecutionStatus.valueOf(source.resultStatus);
        TestExecutionError error = source.errorCode == null ? null : new TestExecutionError(
                TestExecutionErrorStage.valueOf(source.errorStage),
                TestExecutionErrorCode.valueOf(source.errorCode),
                source.errorMessage);
        return switch (status) {
            case SUCCEEDED -> TestExecution.succeeded(
                    id,
                    new ApplicationResponse(source.applicationResponse),
                    new EvaluationResult(Action.valueOf(source.evaluatorVerdict)),
                    source.startedAt,
                    source.completedAt);
            case FAILED -> source.applicationResponse != null && error.stage() == TestExecutionErrorStage.EVALUATOR
                    ? TestExecution.failedAfterApplication(
                            id,
                            new ApplicationResponse(source.applicationResponse),
                            error,
                            source.startedAt,
                            source.completedAt)
                    : TestExecution.failed(id, error, source.startedAt, source.completedAt);
            case TIMED_OUT -> source.applicationResponse != null && error.stage() == TestExecutionErrorStage.EVALUATOR
                    ? TestExecution.timedOutAfterApplication(
                            id,
                            new ApplicationResponse(source.applicationResponse),
                            error,
                            source.startedAt,
                            source.completedAt)
                    : TestExecution.timedOut(id, error, source.startedAt, source.completedAt);
            case NOT_STARTED -> TestExecution.notStarted(id);
        };
    }

}

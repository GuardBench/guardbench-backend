package com.guardbench.testrun.infrastructure.persistence;

import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.ActualResult;
import com.guardbench.testrun.domain.BaselineTarget;
import com.guardbench.testrun.domain.CandidateSource;
import com.guardbench.testrun.domain.CandidateTarget;
import com.guardbench.testrun.domain.ExpectedResult;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.SourceTestCaseId;
import com.guardbench.testrun.domain.SourceTestSuiteId;
import com.guardbench.testrun.domain.TargetType;
import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionError;
import com.guardbench.testrun.domain.TestExecutionErrorCode;
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
        TestRunEntity target = new TestRunEntity();
        target.id = source.id().value();
        target.testSuiteId = source.sourceTestSuiteId().value();
        target.status = source.status().name();
        target.testCaseCount = source.testCaseCount();
        target.processedTestCaseCount = source.processedTestCaseCount();
        target.baselineGuardrailId = source.baselineTarget().guardrailId();
        target.baselineVersion = source.baselineTarget().version();
        target.candidateGuardrailId = source.candidateTarget().guardrailId();
        target.candidateRequestedSource = source.candidateTarget().requestedSource().name();
        target.candidateResolvedVersion = source.candidateTarget().resolvedVersion();
        target.executionOutcome = source.executionOutcome() == null ? null : source.executionOutcome().name();
        target.createdAt = source.timeline().createdAt();
        target.startedAt = source.timeline().startedAt();
        target.completedAt = source.timeline().completedAt();
        target.updatedAt = source.timeline().updatedAt();
        return target;
    }

    static TestRun toDomain(TestRunEntity source) {
        return TestRun.rehydrate(
                new TestRunId(source.id),
                new SourceTestSuiteId(source.testSuiteId),
                new BaselineTarget(source.baselineGuardrailId, source.baselineVersion),
                new CandidateTarget(
                        source.candidateGuardrailId,
                        CandidateSource.valueOf(source.candidateRequestedSource),
                        source.candidateResolvedVersion
                ),
                source.testCaseCount,
                source.processedTestCaseCount,
                TestRunStatus.valueOf(source.status),
                source.executionOutcome == null ? null : TestRunExecutionOutcome.valueOf(source.executionOutcome),
                new TestRunTimeline(source.createdAt, source.startedAt, source.completedAt, source.updatedAt)
        );
    }

    static TestCaseSnapshotEntity toEntity(TestCaseSnapshot source) {
        TestCaseSnapshotEntity target = new TestCaseSnapshotEntity();
        target.id = source.id().value();
        target.testRunId = source.testRunId().value();
        target.sourceTestCaseId = source.sourceTestCaseId().value();
        target.name = source.name();
        target.input = source.input();
        target.expectedAction = source.expectedResult().action().name();
        target.severity = source.severity().name();
        target.category = source.category();
        target.createdAt = source.createdAt();
        return target;
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
        TestExecutionEntity target = new TestExecutionEntity();
        target.id = new TestExecutionEntityId(source.id().snapshotId().value(), source.id().targetType().name());
        target.resultStatus = source.status().name();
        target.actualAction = source.actualResult() == null ? null : source.actualResult().action().name();
        target.errorCode = source.error() == null ? null : source.error().code().name();
        target.errorMessage = source.error() == null ? null : source.error().message();
        target.startedAt = source.startedAt();
        target.completedAt = source.completedAt();
        return target;
    }

    static TestExecution toDomain(TestExecutionEntity source) {
        TestExecutionId id = new TestExecutionId(
                new TestCaseSnapshotId(source.id.snapshotId),
                TargetType.valueOf(source.id.targetType)
        );
        TestExecutionStatus status = TestExecutionStatus.valueOf(source.resultStatus);
        return switch (status) {
            case SUCCEEDED -> TestExecution.succeeded(
                    id, new ActualResult(Action.valueOf(source.actualAction)), source.startedAt, source.completedAt);
            case FAILED -> TestExecution.failed(
                    id,
                    new TestExecutionError(TestExecutionErrorCode.valueOf(source.errorCode), source.errorMessage),
                    source.startedAt,
                    source.completedAt);
            case TIMED_OUT -> TestExecution.timedOut(
                    id,
                    new TestExecutionError(TestExecutionErrorCode.valueOf(source.errorCode), source.errorMessage),
                    source.startedAt,
                    source.completedAt);
            case NOT_STARTED -> TestExecution.notStarted(id);
        };
    }
}

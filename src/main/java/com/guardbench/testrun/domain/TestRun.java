package com.guardbench.testrun.domain;

import java.time.Instant;
import java.util.Objects;

public final class TestRun {

    private final TestRunId id;
    private final SourceTestSuiteId sourceTestSuiteId;
    private final TargetReference targetReference;
    private final EvaluatorReference evaluatorReference;
    private final QualityGatePolicy qualityGatePolicy;
    private final int testCaseCount;
    private int processedTestCaseCount;
    private TestRunStatus status;
    private TestRunExecutionOutcome executionOutcome;
    private TestRunTimeline timeline;

    private TestRun(
            TestRunId id,
            SourceTestSuiteId sourceTestSuiteId,
            TargetReference targetReference,
            EvaluatorReference evaluatorReference,
            QualityGatePolicy qualityGatePolicy,
            int testCaseCount,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "TestRun ID must not be null");
        this.sourceTestSuiteId = Objects.requireNonNull(sourceTestSuiteId, "source TestSuite ID must not be null");
        this.targetReference = Objects.requireNonNull(targetReference, "target reference must not be null");
        this.evaluatorReference = Objects.requireNonNull(evaluatorReference, "evaluator reference must not be null");
        this.qualityGatePolicy = Objects.requireNonNull(qualityGatePolicy, "Quality Gate policy must not be null");
        if (testCaseCount <= 0) {
            throw new IllegalArgumentException("test case count must be positive");
        }
        this.testCaseCount = testCaseCount;
        this.timeline = TestRunTimeline.created(createdAt);
        this.status = TestRunStatus.QUEUED;
    }

    public static TestRun queue(
            TestRunId id,
            SourceTestSuiteId sourceTestSuiteId,
            TargetReference targetReference,
            EvaluatorReference evaluatorReference,
            int testCaseCount,
            Instant createdAt
    ) {
        return queue(
                id,
                sourceTestSuiteId,
                targetReference,
                evaluatorReference,
                QualityGatePolicy.defaultPolicy(),
                testCaseCount,
                createdAt);
    }

    public static TestRun queue(
            TestRunId id,
            SourceTestSuiteId sourceTestSuiteId,
            TargetReference targetReference,
            EvaluatorReference evaluatorReference,
            QualityGatePolicy qualityGatePolicy,
            int testCaseCount,
            Instant createdAt
    ) {
        return new TestRun(
                id, sourceTestSuiteId, targetReference, evaluatorReference, qualityGatePolicy, testCaseCount, createdAt);
    }

    public static TestRun rehydrate(
            TestRunId id,
            SourceTestSuiteId sourceTestSuiteId,
            TargetReference targetReference,
            EvaluatorReference evaluatorReference,
            QualityGatePolicy qualityGatePolicy,
            int testCaseCount,
            int processedTestCaseCount,
            TestRunStatus status,
            TestRunExecutionOutcome executionOutcome,
            TestRunTimeline timeline
    ) {
        TestRun testRun = new TestRun(
                id,
                sourceTestSuiteId,
                targetReference,
                evaluatorReference,
                qualityGatePolicy,
                testCaseCount,
                timeline.createdAt()
        );
        if (processedTestCaseCount < 0 || processedTestCaseCount > testCaseCount) {
            throw new IllegalArgumentException("processed TestCase count must be between zero and total count");
        }
        testRun.processedTestCaseCount = processedTestCaseCount;
        testRun.status = Objects.requireNonNull(status, "TestRun status must not be null");
        testRun.executionOutcome = executionOutcome;
        testRun.timeline = Objects.requireNonNull(timeline, "TestRun timeline must not be null");
        return testRun;
    }

    public void beginPreparing(Instant preparedAt) {
        requireStatus(TestRunStatus.QUEUED, "begin preparation");
        timeline = timeline.start(preparedAt);
        status = TestRunStatus.PREPARING;
    }

    public void beginRunning(Instant runningAt) {
        requireStatus(TestRunStatus.PREPARING, "begin execution");
        timeline = timeline.touch(runningAt);
        status = TestRunStatus.RUNNING;
    }

    public void updateProgress(TestRunExecutionSummary executionSummary, Instant updatedAt) {
        requireStatus(TestRunStatus.RUNNING, "update progress");
        validateSummary(executionSummary);
        processedTestCaseCount = executionSummary.processedTestCaseCount();
        timeline = timeline.touch(updatedAt);
    }

    public void finish(TestRunExecutionSummary executionSummary, Instant completedAt) {
        requireStatus(TestRunStatus.RUNNING, "finish");
        validateSummary(executionSummary);
        TestRunExecutionOutcome outcome = executionSummary.outcome();
        timeline = timeline.complete(completedAt);
        processedTestCaseCount = executionSummary.processedTestCaseCount();
        executionOutcome = outcome;
        status = TestRunStatus.FINISHED;
    }

    public void failPreparation(Instant completedAt) {
        requireStatus(TestRunStatus.PREPARING, "fail preparation");
        timeline = timeline.complete(completedAt);
        processedTestCaseCount = testCaseCount;
        executionOutcome = TestRunExecutionOutcome.ERROR;
        status = TestRunStatus.FINISHED;
    }

    public TestRunId id() {
        return id;
    }

    public SourceTestSuiteId sourceTestSuiteId() {
        return sourceTestSuiteId;
    }

    public TargetReference targetReference() {
        return targetReference;
    }

    public EvaluatorReference evaluatorReference() { return evaluatorReference; }

    public QualityGatePolicy qualityGatePolicy() { return qualityGatePolicy; }

    public int testCaseCount() {
        return testCaseCount;
    }

    public int processedTestCaseCount() {
        return processedTestCaseCount;
    }

    public TestRunStatus status() {
        return status;
    }

    public TestRunExecutionOutcome executionOutcome() {
        return executionOutcome;
    }

    public TestRunTimeline timeline() {
        return timeline;
    }

    private void validateSummary(TestRunExecutionSummary executionSummary) {
        Objects.requireNonNull(executionSummary, "execution summary must not be null");
        if (executionSummary.testCaseCount() != testCaseCount) {
            throw new IllegalArgumentException("execution summary must include every TestCaseSnapshot");
        }
    }

    private void requireStatus(TestRunStatus expectedStatus, String operation) {
        if (status != expectedStatus) {
            throw new IllegalStateException("cannot " + operation + " when TestRun status is " + status);
        }
    }
}

package com.guardbench.testrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.guardbench.testrun.application.port.out.LockTestRunPort;
import com.guardbench.testrun.domain.SnapshotExecutionPair;
import com.guardbench.testrun.domain.TargetType;
import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TestExecutionStatus;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunExecutionSummary;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.repository.TestCaseSnapshotRepository;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;

/**
 * TestRun 최종화를 위한 공개 Application Facade다.
 *
 * <p>ADR 0006에 따라 evaluation Context의 Integration Adapter가 testrun Domain/Repository를
 * 직접 사용하는 대신 이 Facade를 호출한다.
 * Facade는 testrun Domain 접근을 캡슐화하고 스칼라/code 값 기반의
 * {@link TestRunFinalizationFacts}로 결과를 반환한다.
 *
 * <p>ADR 0004에 따라 {@link #requestFinish}는 호출 시점에 같은
 * {@code @Transactional} 범위에 참여한다.
 */
@org.springframework.stereotype.Service
public class TestRunFinalizationFacade {

    private final TestRunRepository testRunRepository;
    private final TestCaseSnapshotRepository snapshotRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final LockTestRunPort lockTestRunPort;
    private final Clock clock;

    public TestRunFinalizationFacade(
            TestRunRepository testRunRepository,
            TestCaseSnapshotRepository snapshotRepository,
            TestExecutionRepository testExecutionRepository,
            LockTestRunPort lockTestRunPort,
            Clock clock
    ) {
        this.testRunRepository = Objects.requireNonNull(testRunRepository);
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository);
        this.testExecutionRepository = Objects.requireNonNull(testExecutionRepository);
        this.lockTestRunPort = Objects.requireNonNull(lockTestRunPort);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 최종화 직렬화를 위해 TestRun 행 잠금을 획득한 뒤 실행 사실을 로드한다.
     *
     * <p>ADR 0005: 호출자의 트랜잭션 범위에서 실행되며, 잠금은 트랜잭션 종료 시 해제된다.
     * 동시 완료 메시지는 이 잠금에서 대기하므로 readiness 확인과 최종화가 직렬화된다.
     *
     * @param testRunId TestRun scalar ID
     * @return 실행 사실, TestRun이 존재하지 않으면 empty
     */
    public Optional<TestRunFinalizationFacts> lockAndLoadFinalizationFacts(long testRunId) {
        if (!lockTestRunPort.lockForUpdate(testRunId)) {
            return Optional.empty();
        }

        Optional<TestRun> testRunOpt = testRunRepository.findById(new TestRunId(testRunId));
        if (testRunOpt.isEmpty()) {
            return Optional.empty();
        }

        TestRun testRun = testRunOpt.get();
        List<TestCaseSnapshot> snapshots = snapshotRepository.findAllByTestRunId(new TestRunId(testRunId));

        List<TestRunFinalizationFacts.SnapshotExecutionFact> facts = new ArrayList<>();

        for (TestCaseSnapshot snapshot : snapshots) {
            TestCaseSnapshotId snapshotId = snapshot.id();

            facts.add(new TestRunFinalizationFacts.SnapshotExecutionFact(
                    snapshotId.value(),
                    snapshot.expectedResult().action().name(),
                    toTargetFact(snapshotId, TargetType.BASELINE),
                    toTargetFact(snapshotId, TargetType.CANDIDATE)
            ));
        }

        return Optional.of(new TestRunFinalizationFacts(
                testRunId,
                testRun.status().name(),
                testRun.testCaseCount(),
                facts
        ));
    }

    private TestRunFinalizationFacts.TargetExecutionFact toTargetFact(
            TestCaseSnapshotId snapshotId,
            TargetType targetType
    ) {
        return testExecutionRepository.findById(new TestExecutionId(snapshotId, targetType))
                .map(TestRunFinalizationFacade::toTargetFact)
                .orElseGet(TestRunFinalizationFacts.TargetExecutionFact::notExecuted);
    }

    private static TestRunFinalizationFacts.TargetExecutionFact toTargetFact(TestExecution execution) {
        boolean succeeded = execution.status() == TestExecutionStatus.SUCCEEDED;
        return new TestRunFinalizationFacts.TargetExecutionFact(
                execution.status().isTerminal(),
                execution.status().name(),
                succeeded ? execution.actualResult().action().name() : null
        );
    }

    /**
     * TestRun을 FINISHED 상태로 전환한다.
     *
     * <p>ADR 0004에 따라 호출자의 @Transactional 범위에 참여한다.
     *
     * @param testRunId TestRun scalar ID
     * @param executionOutcomeCode TestRunExecutionOutcome code (COMPLETED, INCOMPLETE, ERROR)
     * @param processedTestCaseCount 처리된 TestCase 수
     * @param testCaseCount 전체 TestCase 수
     * @throws IllegalStateException TestRun이 FINISHED 가능 상태가 아닌 경우
     */
    public void requestFinish(long testRunId, String executionOutcomeCode, int processedTestCaseCount, int testCaseCount) {
        TestRun testRun = testRunRepository.findById(new TestRunId(testRunId))
                .orElseThrow(() -> new IllegalStateException(
                        "TestRun not found for finalization. testRunId=" + testRunId));

        List<SnapshotExecutionPair> pairs = buildPairsForFinalization(testRunId);
        TestRunExecutionSummary summary = TestRunExecutionSummary.from(pairs);

        Instant completedAt = clock.instant();
        testRun.finish(summary, completedAt);
        testRunRepository.save(testRun);
    }

    private List<SnapshotExecutionPair> buildPairsForFinalization(long testRunId) {
        List<TestCaseSnapshot> snapshots = snapshotRepository.findAllByTestRunId(new TestRunId(testRunId));

        List<SnapshotExecutionPair> pairs = new ArrayList<>();
        for (TestCaseSnapshot snapshot : snapshots) {
            TestCaseSnapshotId snapshotId = snapshot.id();
            TestExecutionId baselineId = new TestExecutionId(snapshotId, TargetType.BASELINE);
            TestExecutionId candidateId = new TestExecutionId(snapshotId, TargetType.CANDIDATE);

            TestExecutionStatus baselineStatus = testExecutionRepository.findById(baselineId)
                    .map(TestExecution::status)
                    .orElse(null);
            TestExecutionStatus candidateStatus = testExecutionRepository.findById(candidateId)
                    .map(TestExecution::status)
                    .orElse(null);

            pairs.add(new SnapshotExecutionPair(snapshotId, baselineStatus, candidateStatus));
        }
        return pairs;
    }
}

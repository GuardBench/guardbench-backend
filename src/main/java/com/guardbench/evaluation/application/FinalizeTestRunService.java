package com.guardbench.evaluation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.evaluation.application.port.out.FinalizeTestRunPort;
import com.guardbench.evaluation.application.port.out.LoadTestRunExecutionFactsPort;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts.SnapshotExecutionFact;
import com.guardbench.evaluation.domain.EvaluationAction;
import com.guardbench.evaluation.domain.QualityGateEvaluator;
import com.guardbench.evaluation.domain.QualityGateResult;
import com.guardbench.evaluation.domain.SnapshotEvaluation;
import com.guardbench.evaluation.domain.SnapshotEvaluationReference;
import com.guardbench.evaluation.domain.SnapshotEvaluator;
import com.guardbench.evaluation.domain.TestRunEvaluationReference;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;
import com.guardbench.evaluation.domain.repository.SnapshotEvaluationRepository;

/**
 * Evaluation 최종화 Application Service다.
 *
 * <p>ADR 0004에 따라 QualityGateResult 저장과 TestRun FINISHED 전환을
 * 하나의 트랜잭션에서 원자적으로 수행한다.
 *
 * <p>ADR 0005 Orchestrator는 각 TestExecutionCompleted 메시지를 받을 때
 * 모든 Snapshot이 처리 완료되었는지 확인한 후 이 서비스를 호출한다.
 *
 * <p>ADR 0006에 따라 TestRun Domain 타입을 직접 import하지 않고
 * consumer-owned Port와 scalar 값 계약을 사용한다.
 */
public class FinalizeTestRunService {

    private static final Logger log = LoggerFactory.getLogger(FinalizeTestRunService.class);
    private final LoadTestRunExecutionFactsPort loadExecutionFactsPort;
    private final FinalizeTestRunPort finalizeTestRunPort;
    private final QualityGateResultRepository qualityGateResultRepository;
    private final SnapshotEvaluationRepository snapshotEvaluationRepository;
    private final SnapshotEvaluator snapshotEvaluator;
    private final QualityGateEvaluator qualityGateEvaluator;
    private final Clock clock;

    public FinalizeTestRunService(
            LoadTestRunExecutionFactsPort loadExecutionFactsPort,
            FinalizeTestRunPort finalizeTestRunPort,
            QualityGateResultRepository qualityGateResultRepository,
            SnapshotEvaluationRepository snapshotEvaluationRepository,
            SnapshotEvaluator snapshotEvaluator,
            QualityGateEvaluator qualityGateEvaluator,
            Clock clock
    ) {
        this.loadExecutionFactsPort = Objects.requireNonNull(loadExecutionFactsPort);
        this.finalizeTestRunPort = Objects.requireNonNull(finalizeTestRunPort);
        this.qualityGateResultRepository = Objects.requireNonNull(qualityGateResultRepository);
        this.snapshotEvaluationRepository = Objects.requireNonNull(snapshotEvaluationRepository);
        this.snapshotEvaluator = Objects.requireNonNull(snapshotEvaluator);
        this.qualityGateEvaluator = Objects.requireNonNull(qualityGateEvaluator);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * TestRun 최종화를 수행한다.
     *
     * <p>이미 FINISHED이고 QualityGateResult가 존재하면 기존 결과를 반환하는 멱등 성공이다.
     * 재계산하거나 덮어쓰지 않는다.
     *
     * <p>ADR 0004: readiness 확인, 평가 저장, Quality Gate 저장, TestRun FINISHED 전환을
     * 하나의 트랜잭션으로 실행한다. 외부 Provider 호출이 없는 경로이므로
     * 진입 메서드 전체를 트랜잭션 경계로 선언한다.
     *
     * <p>ADR 0005: 같은 트랜잭션 시작 시 TestRun 행을 잠가 동시 완료 메시지를 직렬화한다.
     *
     * @param testRunId TestRun scalar ID
     * @return 최종화 결과
     */
    @Transactional
    public FinalizationOutcome finalize(long testRunId) {
        long finalizationStartedNanos = System.nanoTime();
        log.info("TestRun finalization started. testRunId={}", testRunId);
        TestRunEvaluationReference reference = new TestRunEvaluationReference(testRunId);

        // ADR 0005: 판정과 저장을 직렬화하기 위해 TestRun 행 잠금을 먼저 획득한다.
        // 잠금 이후에 Quality Gate 존재를 확인해야 동시 완료 메시지가
        // 먼저 commit된 결과를 관찰하고 멱등 성공으로 수렴한다.
        TestRunExecutionFacts facts = loadExecutionFactsPort.lockAndLoad(testRunId)
                .orElse(null);
        if (facts == null) {
            log.warn("TestRun finalization skipped because TestRun was not found. testRunId={} elapsedMs={}",
                    testRunId, elapsedMs(finalizationStartedNanos));
            return FinalizationOutcome.notFound();
        }

        // 이미 완료된 최종화의 재호출: 멱등 성공
        Optional<QualityGateResult> existing = qualityGateResultRepository.findById(reference);
        if (existing.isPresent()) {
            log.info("TestRun finalization already completed. testRunId={} qualityGateStatus={} elapsedMs={}",
                    testRunId, existing.get().status(), elapsedMs(finalizationStartedNanos));
            return FinalizationOutcome.alreadyFinalized(existing.get());
        }

        // FINISHED인데 QualityGateResult가 없으면 불변식 위반
        if ("FINISHED".equals(facts.testRunStatus())) {
            log.error("TestRun finalization invariant violation. testRunId={} status={} elapsedMs={}",
                    testRunId, facts.testRunStatus(), elapsedMs(finalizationStartedNanos));
            return FinalizationOutcome.invariantViolation();
        }

        // RUNNING이 아니면 최종화 불가 (QUEUED, PREPARING은 불가)
        if (!"RUNNING".equals(facts.testRunStatus())) {
            log.info("TestRun finalization not ready. testRunId={} status={} elapsedMs={}",
                    testRunId, facts.testRunStatus(), elapsedMs(finalizationStartedNanos));
            return FinalizationOutcome.notReady();
        }

        // ADR 0005 4단계: Snapshot이 모두 준비되고 모든 pair가 terminal일 때만 최종화한다.
        // 일부만 끝난 시점에 Quality Gate를 먼저 저장하면 TestRun이 RUNNING에 잔류할 수 있다.
        // 미완료 시에도 목록·상세 조회 진행률 계약을 만족시키기 위해 같은 잠금 트랜잭션에서
        // 절대 진행도를 먼저 갱신한 뒤 NotReady로 반환한다.
        boolean snapshotsReady = facts.snapshotFacts().size() == facts.testCaseCount();
        boolean allExecutionsTerminal = facts.snapshotFacts().stream().allMatch(SnapshotExecutionFact::terminal);
        long terminalExecutionCount = facts.snapshotFacts().stream().filter(SnapshotExecutionFact::terminal).count();
        log.info("TestRun finalization readiness checked. testRunId={} snapshots={} expected={} terminalPairs={} snapshotsReady={} allPairsTerminal={}",
                testRunId, facts.snapshotFacts().size(), facts.testCaseCount(), terminalExecutionCount,
                snapshotsReady, allExecutionsTerminal);
        if (!snapshotsReady || !allExecutionsTerminal) {
            if (snapshotsReady) {
                finalizeTestRunPort.updateProgress(testRunId);
            }
            log.info("TestRun finalization not ready after readiness check. testRunId={} snapshots={} expected={} terminalPairs={} snapshotsReady={} allPairsTerminal={} elapsedMs={}",
                    testRunId, facts.snapshotFacts().size(), facts.testCaseCount(), terminalExecutionCount,
                    snapshotsReady, allExecutionsTerminal, elapsedMs(finalizationStartedNanos));
            return FinalizationOutcome.notReady();
        }

        Instant now = clock.instant();

        // Snapshot 평가
        List<SnapshotEvaluation> evaluations = evaluateSnapshots(facts, now);

        // 절대 개수로 진행도와 성공 pair 수를 재계산한다.
        int processedTestCaseCount = (int) facts.snapshotFacts().stream()
                .filter(SnapshotExecutionFact::terminal)
                .count();
        long successfulExecutionCount = facts.snapshotFacts().stream()
                .filter(SnapshotExecutionFact::succeeded)
                .count();

        // Quality Gate 계산
        QualityGateResult qualityGateResult = qualityGateEvaluator.evaluate(
                reference,
                evaluations,
                facts.testCaseCount(),
                successfulExecutionCount,
                now
        );

        // Execution outcome 결정
        String executionOutcomeCode = determineOutcomeCode(facts);

        // 원자적 저장: QualityGateResult + TestRun FINISHED 전환
        qualityGateResultRepository.save(qualityGateResult);
        finalizeTestRunPort.finalize(
                testRunId,
                executionOutcomeCode,
                processedTestCaseCount,
                facts.testCaseCount()
        );

        log.info("TestRun finalization completed. testRunId={} qualityGateStatus={} executionOutcome={} processedTestCaseCount={} testCaseCount={} elapsedMs={}",
                testRunId, qualityGateResult.status(), executionOutcomeCode, processedTestCaseCount,
                facts.testCaseCount(), elapsedMs(finalizationStartedNanos));

        return FinalizationOutcome.finalized(qualityGateResult);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private List<SnapshotEvaluation> evaluateSnapshots(TestRunExecutionFacts facts, Instant now) {
        List<SnapshotEvaluation> evaluations = new ArrayList<>();
        for (SnapshotExecutionFact fact : facts.snapshotFacts()) {
            Optional<SnapshotEvaluation> result = evaluateSnapshot(fact, now);
            result.ifPresent(evaluations::add);
        }
        return evaluations;
    }

    private Optional<SnapshotEvaluation> evaluateSnapshot(SnapshotExecutionFact fact, Instant now) {
        SnapshotEvaluationReference reference = new SnapshotEvaluationReference(fact.snapshotId());

        // 이미 평가된 Snapshot은 기존 결과 사용 (재계산하지 않음)
        Optional<SnapshotEvaluation> existing = snapshotEvaluationRepository.findById(reference);
        if (existing.isPresent()) {
            return existing;
        }

        EvaluationAction expectedAction = toAction(fact.expectedActionCode());
        EvaluationAction evaluatorVerdict = fact.execution().evaluatorVerdictCode() != null
                ? toAction(fact.execution().evaluatorVerdictCode()) : null;

        Optional<SnapshotEvaluation> newEvaluation = snapshotEvaluator.evaluate(
                reference,
                expectedAction,
                evaluatorVerdict,
                now
        );

        // 새로 생성된 평가만 저장한다
        newEvaluation.ifPresent(snapshotEvaluationRepository::save);
        return newEvaluation;
    }

    private static EvaluationAction toAction(String code) {
        return switch (code) {
            case "ALLOW" -> EvaluationAction.ALLOW;
            case "BLOCK" -> EvaluationAction.BLOCK;
            default -> throw new IllegalArgumentException("Unknown action code: " + code);
        };
    }

    private static String determineOutcomeCode(TestRunExecutionFacts facts) {
        long succeededExecutions = facts.snapshotFacts().stream()
                .filter(SnapshotExecutionFact::succeeded)
                .count();

        if (succeededExecutions == facts.testCaseCount()) {
            return "COMPLETED";
        }
        if (succeededExecutions > 0) {
            return "INCOMPLETE";
        }
        return "ERROR";
    }

    /**
     * 최종화 결과를 나타낸다.
     */
    public sealed interface FinalizationOutcome {

        record Finalized(QualityGateResult result) implements FinalizationOutcome {}
        record AlreadyFinalized(QualityGateResult result) implements FinalizationOutcome {}
        record NotFound() implements FinalizationOutcome {}
        record NotReady() implements FinalizationOutcome {}
        record InvariantViolation() implements FinalizationOutcome {}

        static FinalizationOutcome finalized(QualityGateResult result) {
            return new Finalized(result);
        }

        static FinalizationOutcome alreadyFinalized(QualityGateResult result) {
            return new AlreadyFinalized(result);
        }

        static FinalizationOutcome notFound() {
            return new NotFound();
        }

        static FinalizationOutcome notReady() {
            return new NotReady();
        }

        static FinalizationOutcome invariantViolation() {
            return new InvariantViolation();
        }
    }
}

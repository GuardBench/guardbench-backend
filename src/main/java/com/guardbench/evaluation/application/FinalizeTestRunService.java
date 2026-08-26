package com.guardbench.evaluation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.guardbench.evaluation.application.port.out.FinalizeTestRunPort;
import com.guardbench.evaluation.application.port.out.LoadTestRunExecutionFactsPort;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts.SnapshotExecutionFact;
import com.guardbench.evaluation.domain.EvaluationAction;
import com.guardbench.evaluation.domain.QualityGateEvaluator;
import com.guardbench.evaluation.domain.QualityGateResult;
import com.guardbench.evaluation.domain.QualityGateStatus;
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
     * @param testRunId TestRun scalar ID
     * @return 최종화 결과
     */
    @Transactional
    public FinalizationOutcome finalize(long testRunId) {
        TestRunEvaluationReference reference = new TestRunEvaluationReference(testRunId);

        // 이미 완료된 최종화의 재호출: 멱등 성공
        Optional<QualityGateResult> existing = qualityGateResultRepository.findById(reference);
        if (existing.isPresent()) {
            return FinalizationOutcome.alreadyFinalized(existing.get());
        }

        // 실행 사실 로드
        TestRunExecutionFacts facts = loadExecutionFactsPort.load(testRunId)
                .orElse(null);
        if (facts == null) {
            return FinalizationOutcome.notFound();
        }

        // FINISHED인데 QualityGateResult가 없으면 불변식 위반
        if ("FINISHED".equals(facts.testRunStatus())) {
            return FinalizationOutcome.invariantViolation();
        }

        // RUNNING이 아니면 최종화 불가 (QUEUED, PREPARING은 불가)
        if (!"RUNNING".equals(facts.testRunStatus())) {
            return FinalizationOutcome.notReady();
        }

        Instant now = clock.instant();

        // Snapshot 평가
        List<SnapshotEvaluation> evaluations = evaluateSnapshots(facts, now);

        // Quality Gate 계산
        QualityGateResult qualityGateResult = qualityGateEvaluator.evaluate(
                reference,
                evaluations,
                facts.testCaseCount(),
                facts.successfulExecutionPairCount(),
                now
        );

        // Execution outcome 결정
        String executionOutcomeCode = determineOutcomeCode(facts);

        // 원자적 저장: QualityGateResult + TestRun FINISHED 전환
        qualityGateResultRepository.save(qualityGateResult);
        finalizeTestRunPort.finalize(
                testRunId,
                executionOutcomeCode,
                facts.testCaseCount(),
                facts.testCaseCount()
        );

        return FinalizationOutcome.finalized(qualityGateResult);
    }

    /**
     * 대상 준비 실패 시 NOT_EVALUATED Quality Gate를 생성하고
     * TestRun FINISHED/ERROR를 원자적으로 저장한다.
     *
     * <p>ADR 0004/0005: materialization 등 대상 준비가 실패하여 실행 불가한 경우
     * PREPARING → FINISHED 예외 경로다.
     *
     * @param testRunId TestRun scalar ID
     * @param testCaseCount 전체 TestCase 수
     * @return 최종화 결과
     */
    public FinalizationOutcome finalizePreparationFailure(long testRunId, int testCaseCount) {
        TestRunEvaluationReference reference = new TestRunEvaluationReference(testRunId);

        // 이미 완료된 최종화의 재호출: 멱등 성공
        Optional<QualityGateResult> existing = qualityGateResultRepository.findById(reference);
        if (existing.isPresent()) {
            return FinalizationOutcome.alreadyFinalized(existing.get());
        }

        Instant now = clock.instant();

        // NOT_EVALUATED Quality Gate 저장
        QualityGateResult notEvaluated = new QualityGateResult(
                reference,
                QualityGateStatus.NOT_EVALUATED,
                null,
                now
        );
        qualityGateResultRepository.save(notEvaluated);

        return FinalizationOutcome.finalized(notEvaluated);
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
        EvaluationAction baselineAction = fact.baselineActionCode() != null
                ? toAction(fact.baselineActionCode()) : null;
        EvaluationAction candidateAction = fact.candidateActionCode() != null
                ? toAction(fact.candidateActionCode()) : null;

        boolean comparisonConditionsSatisfied = fact.baselineSucceeded() && fact.candidateSucceeded();

        Optional<SnapshotEvaluation> newEvaluation = snapshotEvaluator.evaluate(
                reference,
                expectedAction,
                baselineAction,
                candidateAction,
                comparisonConditionsSatisfied,
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
        long totalExecutions = (long) facts.testCaseCount() * 2;
        long succeededExecutions = facts.successfulExecutionPairCount() * 2;
        // Also count individual successes from snapshot facts
        long actualSucceeded = facts.snapshotFacts().stream()
                .mapToLong(f -> (f.baselineSucceeded() ? 1L : 0L) + (f.candidateSucceeded() ? 1L : 0L))
                .sum();

        if (actualSucceeded == totalExecutions) {
            return "COMPLETED";
        }
        if (actualSucceeded > 0) {
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

package com.guardbench.testrun.support.fixture;

import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.guardbench.evaluation.application.FinalizeTestRunService;
import com.guardbench.evaluation.application.port.out.FinalizeTestRunPort;
import com.guardbench.evaluation.application.port.out.LoadTestRunExecutionFactsPort;
import com.guardbench.evaluation.domain.QualityGateEvaluator;
import com.guardbench.evaluation.domain.SnapshotEvaluator;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;
import com.guardbench.evaluation.domain.repository.SnapshotEvaluationRepository;
import com.guardbench.testrun.application.ExecuteTestRunService;
import com.guardbench.testrun.application.ResolveTestRunService;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionRequest;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;
import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetExecutionRequest;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetPreparationPort;
import com.guardbench.testrun.application.port.out.TargetPreparationRequest;
import com.guardbench.testrun.application.port.out.LoadExecutionContextPort;
import com.guardbench.testrun.application.port.out.LoadSnapshotIdsByTestRunPort;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.ResolutionClaimPort;
import com.guardbench.testrun.application.port.out.SaveNotEvaluatedQualityGatePort;
import com.guardbench.testrun.application.port.out.TransactionalPhasePort;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;

/**
 * #19 통합 테스트를 위해 Worker Application Service 전체 체인을
 * Guardrail Test Adapter(람다)로 조립하는 지원 컴포넌트다.
 *
 * <p>{@code guardbench.worker.enabled=true} 없이도 실제 Repository/Port
 * Spring Bean(claim, outbox, execution context 등)을 그대로 재사용하고,
 * Provider 관련 두 Port({@link TargetPreparationPort}, {@link TargetExecutionPort})만
 * Test Adapter로 대체한다. LocalStack이나 실제 AWS 자격 증명이 필요하지 않다.
 *
 * <p>{@link FinalizeTestRunService#finalize}는 {@code @Transactional}이 선언 메서드
 * 자체에 있어 Spring Bean(CGLIB 프록시)으로 등록해야만 트랜잭션이 적용된다.
 * 이 클래스는 {@code @TestConfiguration}의 {@code @Bean} 메서드로 등록해
 * 운영 조립과 동일한 프록시를 갖도록 한다({@code TestRunFinalizationConcurrencyIntegrationTest}와 동일 패턴).
 *
 * <p>{@link #prepare(Consumer)}와 {@link #execute(Function)}으로 각 시나리오가
 * 원하는 Target 준비 또는 Provider 응답을 설정한 뒤,
 * {@link #resolveService()}·{@link #executeService()}·{@link #finalizeService}로
 * Resolve → Execute → Finalize를 순차 호출하면
 * TestSuite → TestCase → TestRun 전체 흐름을 SQS/Bedrock 없이 재현할 수 있다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class WorkerChainTestSupport {

    @Bean
    FinalizeTestRunService finalizeTestRunService(
            LoadTestRunExecutionFactsPort loadExecutionFactsPort,
            FinalizeTestRunPort finalizeTestRunPort,
            QualityGateResultRepository qualityGateResultRepository,
            SnapshotEvaluationRepository snapshotEvaluationRepository,
            Clock clock
    ) {
        return new FinalizeTestRunService(
                loadExecutionFactsPort,
                finalizeTestRunPort,
                qualityGateResultRepository,
                snapshotEvaluationRepository,
                new SnapshotEvaluator(),
                new QualityGateEvaluator(),
                clock
        );
    }

    @Bean
    WorkerChain workerChain(
            ResolutionClaimPort resolutionClaimPort,
            TestRunRepository testRunRepository,
            LoadSnapshotIdsByTestRunPort loadSnapshotIdsPort,
            OutboxPort outboxPort,
            TestExecutionRepository testExecutionRepository,
            SaveNotEvaluatedQualityGatePort saveNotEvaluatedQualityGatePort,
            TransactionalPhasePort transactionalPhasePort,
            ExecutionClaimPort executionClaimPort,
            LoadExecutionContextPort loadExecutionContextPort,
            FinalizeTestRunService finalizeTestRunService,
            Clock clock
    ) {
        return new WorkerChain(
                resolutionClaimPort,
                testRunRepository,
                loadSnapshotIdsPort,
                outboxPort,
                testExecutionRepository,
                saveNotEvaluatedQualityGatePort,
                transactionalPhasePort,
                executionClaimPort,
                loadExecutionContextPort,
                finalizeTestRunService,
                clock
        );
    }

    /**
     * Resolve/Execute Test Adapter 조립과 전체 체인 실행을 제공하는 헬퍼다.
     *
     * <p>{@link ResolveTestRunService}와 {@link ExecuteTestRunService}는
     * {@code @Transactional} 메서드 전체가 아니라 {@link TransactionalPhasePort}로
     * phase 단위 트랜잭션을 선언하므로 프록시 없이 직접 조립해도 안전하다.
     */
    @Component
    public static class WorkerChain {

        private final ResolutionClaimPort resolutionClaimPort;
        private final TestRunRepository testRunRepository;
        private final LoadSnapshotIdsByTestRunPort loadSnapshotIdsPort;
        private final OutboxPort outboxPort;
        private final TestExecutionRepository testExecutionRepository;
        private final SaveNotEvaluatedQualityGatePort saveNotEvaluatedQualityGatePort;
        private final TransactionalPhasePort transactionalPhasePort;
        private final ExecutionClaimPort executionClaimPort;
        private final LoadExecutionContextPort loadExecutionContextPort;
        private final FinalizeTestRunService finalizeTestRunService;
        private final Clock clock;

        private Consumer<TargetPreparationRequest> preparationBehavior;
        private Function<TargetExecutionRequest, TargetExecutionResult> executionBehavior;
        private Function<EvaluatorExecutionRequest, EvaluatorExecutionResult> evaluatorBehavior;

        @Autowired
        WorkerChain(
                ResolutionClaimPort resolutionClaimPort,
                TestRunRepository testRunRepository,
                LoadSnapshotIdsByTestRunPort loadSnapshotIdsPort,
                OutboxPort outboxPort,
                TestExecutionRepository testExecutionRepository,
                SaveNotEvaluatedQualityGatePort saveNotEvaluatedQualityGatePort,
                TransactionalPhasePort transactionalPhasePort,
                ExecutionClaimPort executionClaimPort,
                LoadExecutionContextPort loadExecutionContextPort,
                FinalizeTestRunService finalizeTestRunService,
                Clock clock
        ) {
            this.resolutionClaimPort = resolutionClaimPort;
            this.testRunRepository = testRunRepository;
            this.loadSnapshotIdsPort = loadSnapshotIdsPort;
            this.outboxPort = outboxPort;
            this.testExecutionRepository = testExecutionRepository;
            this.saveNotEvaluatedQualityGatePort = saveNotEvaluatedQualityGatePort;
            this.transactionalPhasePort = transactionalPhasePort;
            this.executionClaimPort = executionClaimPort;
            this.loadExecutionContextPort = loadExecutionContextPort;
            this.finalizeTestRunService = finalizeTestRunService;
            this.clock = clock;
            reset();
        }

        /** 각 테스트 시작 전에 이전 시나리오의 Test Adapter 동작을 초기화한다. */
        public void reset() {
            preparationBehavior = request -> { };
            executionBehavior = request -> TargetExecutionResult.succeeded("ALLOW");
            evaluatorBehavior = request -> EvaluatorExecutionResult.succeeded("ALLOW");
        }

        /** Target 준비 동작을 시나리오별로 재정의한다. */
        public void prepare(Consumer<TargetPreparationRequest> behavior) {
            this.preparationBehavior = behavior;
        }

        /** Guardrail 실행 결과를 시나리오별로 재정의한다. */
        public void execute(Function<TargetExecutionRequest, TargetExecutionResult> behavior) {
            this.executionBehavior = behavior;
        }

        /** Evaluator 동작을 시나리오별로 재정의한다. */
        public void evaluate(Function<EvaluatorExecutionRequest, EvaluatorExecutionResult> behavior) {
            this.evaluatorBehavior = behavior;
        }

        public ResolveTestRunService resolveService() {
            return new ResolveTestRunService(
                    resolutionClaimPort,
                    testRunRepository,
                    request -> preparationBehavior.accept(request),
                    loadSnapshotIdsPort,
                    outboxPort,
                    testExecutionRepository,
                    saveNotEvaluatedQualityGatePort,
                    transactionalPhasePort,
                    clock
            );
        }

        public ExecuteTestRunService executeService() {
            return new ExecuteTestRunService(
                    executionClaimPort,
                    testExecutionRepository,
                    loadExecutionContextPort,
                    request -> executionBehavior.apply(request),
                    request -> evaluatorBehavior.apply(request),
                    outboxPort,
                    transactionalPhasePort,
                    clock
            );
        }

        public FinalizeTestRunService finalizeService() {
            return finalizeTestRunService;
        }

        /**
         * Resolve → Execute → Finalize를 모든 Snapshot에 대해
         * 순차 실행한다. Outbox에 fan-out된 이벤트를 SQS로 발행하지 않고
         * Application Service를 직접 호출해 진행시킨다.
         *
         * @param testRunId 접수된 TestRun ID
         * @param snapshotIds Resolve 이후 fan-out 대상 Snapshot ID 목록
         */
        public void runFullWorkerChain(long testRunId, Iterable<Long> snapshotIds) {
            resolveService().resolve(testRunId);
            ExecuteTestRunService executeService = executeService();
            for (long snapshotId : snapshotIds) {
                executeService.execute(snapshotId);
            }
            finalizeTestRunService.finalize(testRunId);
        }
    }
}

package com.guardbench.testrun.infrastructure.integration.evaluation;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guardbench.evaluation.application.FinalizeTestRunService;
import com.guardbench.evaluation.application.port.out.FinalizeTestRunPort;
import com.guardbench.evaluation.application.port.out.LoadTestRunExecutionFactsPort;
import com.guardbench.evaluation.domain.QualityGateEvaluator;
import com.guardbench.evaluation.domain.SnapshotEvaluator;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;
import com.guardbench.evaluation.domain.repository.SnapshotEvaluationRepository;
import com.guardbench.testrun.application.port.in.HandleTestExecutionCompletedPort;

/**
 * Worker 활성화 시 Finalization 경계를 연결하는 Integration 설정이다.
 *
 * <p>ADR 0006에 따라 Integration Adapter 패키지 안에서만
 * evaluation 경계를 넘는 빈 등록과 변환을 허용한다.
 *
 * <p>guardbench.worker.enabled=true일 때만 활성화하여
 * 일반 API 모드에서 불필요한 Worker 의존성을 방지한다.
 */
@Configuration
@ConditionalOnProperty(name = "guardbench.worker.enabled", havingValue = "true")
class EvaluationFinalizationWorkerConfiguration {

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
    HandleTestExecutionCompletedPort handleTestExecutionCompletedPort(
            FinalizeTestRunService finalizeTestRunService
    ) {
        return testRunId -> {
            FinalizeTestRunService.FinalizationOutcome outcome = finalizeTestRunService.finalize(testRunId);
            return switch (outcome) {
                case FinalizeTestRunService.FinalizationOutcome.Finalized ignored -> true;
                case FinalizeTestRunService.FinalizationOutcome.AlreadyFinalized ignored -> true;
                case FinalizeTestRunService.FinalizationOutcome.NotFound ignored -> true;
                case FinalizeTestRunService.FinalizationOutcome.InvariantViolation ignored -> true;
                case FinalizeTestRunService.FinalizationOutcome.NotReady ignored -> false;
            };
        };
    }
}

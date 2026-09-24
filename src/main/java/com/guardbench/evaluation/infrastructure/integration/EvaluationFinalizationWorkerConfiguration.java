package com.guardbench.evaluation.infrastructure.integration;

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
import com.guardbench.testrun.application.CheckTestRunCompletionService;
import com.guardbench.testrun.application.port.in.HandleTestExecutionCompletedPort;

/**
 * Worker 활성화 시 Evaluation 최종화 Application Service와 TestRun 완료 이벤트 경계를 연결한다.
 *
 * <p>Evaluation 소유 설정이므로 Evaluation Domain/Repository를 내부에서 조립할 수 있고,
 * 다른 Context에는 TestRun Application API만 의존한다.
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
            FinalizeTestRunService finalizeTestRunService,
            CheckTestRunCompletionService checkTestRunCompletionService
    ) {
        return testRunId -> {
            if (!checkTestRunCompletionService.check(testRunId)) {
                return true;
            }
            FinalizeTestRunService.FinalizationOutcome outcome = finalizeTestRunService.finalize(testRunId);
            return switch (outcome) {
                case FinalizeTestRunService.FinalizationOutcome.Finalized ignored -> true;
                case FinalizeTestRunService.FinalizationOutcome.AlreadyFinalized ignored -> true;
                case FinalizeTestRunService.FinalizationOutcome.NotFound ignored -> true;
                case FinalizeTestRunService.FinalizationOutcome.InvariantViolation ignored -> true;
                case FinalizeTestRunService.FinalizationOutcome.NotReady ignored -> true;
            };
        };
    }
}

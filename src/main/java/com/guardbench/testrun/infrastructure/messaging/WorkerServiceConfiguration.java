package com.guardbench.testrun.infrastructure.messaging;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guardbench.testrun.application.ExecuteTestRunService;
import com.guardbench.testrun.application.ResolveTestRunService;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;
import com.guardbench.testrun.application.port.out.GuardrailExecutionPort;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationPort;
import com.guardbench.testrun.application.port.out.LoadExecutionContextPort;
import com.guardbench.testrun.application.port.out.LoadSnapshotIdsByTestRunPort;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.ResolutionClaimPort;
import com.guardbench.testrun.application.port.out.SaveNotEvaluatedQualityGatePort;
import com.guardbench.testrun.application.port.out.TransactionalPhasePort;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;

/**
 * Worker Application Service 빈 등록이다.
 *
 * <p>{@code guardbench.worker.enabled=true}일 때만 활성화하여
 * 일반 API 모드에서 Worker 전용 Application Service 의존성을 로딩하지 않는다.
 *
 * <p>{@link ResolveTestRunService}와 {@link ExecuteTestRunService}는
 * {@code new}로 조립되는 plain class로, 기존 Repository/Port/Clock 빈을 주입받는다.
 *
 * <p>두 서비스는 외부 Provider 호출을 사이에 두고 여러 persistence phase를 수행하므로
 * 메서드 전체 트랜잭션 대신 {@link TransactionalPhasePort}로 phase 경계를 주입받는다.
 */
@Configuration
@ConditionalOnProperty(name = "guardbench.worker.enabled", havingValue = "true")
class WorkerServiceConfiguration {

    @Bean
    ResolveTestRunService resolveTestRunService(
            ResolutionClaimPort resolutionClaimPort,
            TestRunRepository testRunRepository,
            GuardrailMaterializationPort materializationPort,
            LoadSnapshotIdsByTestRunPort loadSnapshotIdsPort,
            OutboxPort outboxPort,
            TestExecutionRepository testExecutionRepository,
            SaveNotEvaluatedQualityGatePort saveNotEvaluatedQualityGatePort,
            TransactionalPhasePort transactionalPhasePort,
            Clock clock
    ) {
        return new ResolveTestRunService(
                resolutionClaimPort,
                testRunRepository,
                materializationPort,
                loadSnapshotIdsPort,
                outboxPort,
                testExecutionRepository,
                saveNotEvaluatedQualityGatePort,
                transactionalPhasePort,
                clock
        );
    }

    @Bean
    ExecuteTestRunService executeTestRunService(
            ExecutionClaimPort executionClaimPort,
            TestExecutionRepository testExecutionRepository,
            LoadExecutionContextPort loadExecutionContextPort,
            GuardrailExecutionPort guardrailExecutionPort,
            OutboxPort outboxPort,
            TransactionalPhasePort transactionalPhasePort,
            Clock clock
    ) {
        return new ExecuteTestRunService(
                executionClaimPort,
                testExecutionRepository,
                loadExecutionContextPort,
                guardrailExecutionPort,
                outboxPort,
                transactionalPhasePort,
                clock
        );
    }
}

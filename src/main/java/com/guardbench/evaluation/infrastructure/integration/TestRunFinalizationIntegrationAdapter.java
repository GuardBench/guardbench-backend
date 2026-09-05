package com.guardbench.evaluation.infrastructure.integration;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.guardbench.evaluation.application.port.out.FinalizeTestRunPort;
import com.guardbench.evaluation.application.port.out.LoadTestRunExecutionFactsPort;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts.SnapshotExecutionFact;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts.TargetExecutionFact;
import com.guardbench.testrun.application.TestRunFinalizationFacade;
import com.guardbench.testrun.application.TestRunFinalizationFacts;

/**
 * Evaluation Context의 outbound Port를 TestRun Application 경계로 연결하는 Integration Adapter다.
 *
 * <p>ADR 0006에 따라 이 Adapter는 testrun Domain/Repository를 직접 사용하지 않고
 * testrun Application Facade({@link TestRunFinalizationFacade})만 호출한다.
 * Evaluation Core는 testrun Domain 타입을 직접 import하지 않으며
 * Integration Adapter가 Facade의 스칼라 값을 Evaluation 소유 Port 계약으로 변환한다.
 *
 * <p>ADR 0004에 따라 FinalizeTestRunPort의 구현은 호출 시점에
 * 같은 @Transactional 범위에 참여한다.
 */
@Component
class TestRunFinalizationIntegrationAdapter implements LoadTestRunExecutionFactsPort, FinalizeTestRunPort {

    private final TestRunFinalizationFacade testRunFinalizationFacade;

    TestRunFinalizationIntegrationAdapter(TestRunFinalizationFacade testRunFinalizationFacade) {
        this.testRunFinalizationFacade = Objects.requireNonNull(testRunFinalizationFacade);
    }

    @Override
    public Optional<TestRunExecutionFacts> lockAndLoad(long testRunId) {
        return testRunFinalizationFacade.lockAndLoadFinalizationFacts(testRunId)
                .map(TestRunFinalizationIntegrationAdapter::toEvaluationFacts);
    }

    @Override
    public void finalize(long testRunId, String executionOutcomeCode, int processedTestCaseCount, int testCaseCount) {
        testRunFinalizationFacade.requestFinish(testRunId, executionOutcomeCode, processedTestCaseCount, testCaseCount);
    }

    @Override
    public void updateProgress(long testRunId) {
        testRunFinalizationFacade.requestProgressUpdate(testRunId);
    }

    private static TestRunExecutionFacts toEvaluationFacts(TestRunFinalizationFacts source) {
        var snapshotFacts = source.snapshotFacts().stream()
                .map(TestRunFinalizationIntegrationAdapter::toEvaluationSnapshotFact)
                .toList();

        return new TestRunExecutionFacts(
                source.testRunId(),
                source.testRunStatus(),
                source.testCaseCount(),
                source.evaluatorReference(),
                source.assertionPassRateThreshold(),
                source.executionSuccessRateThreshold(),
                snapshotFacts
        );
    }

    private static SnapshotExecutionFact toEvaluationSnapshotFact(
            TestRunFinalizationFacts.SnapshotExecutionFact source) {
        return new SnapshotExecutionFact(
                source.snapshotId(),
                source.expectedActionCode(),
                toEvaluationTargetFact(source.execution())
        );
    }

    private static TargetExecutionFact toEvaluationTargetFact(
            TestRunFinalizationFacts.TargetExecutionFact source) {
        return new TargetExecutionFact(source.terminal(), source.statusCode(), source.actionCode());
    }
}

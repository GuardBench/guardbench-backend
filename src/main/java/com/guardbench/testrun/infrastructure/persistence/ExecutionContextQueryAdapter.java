package com.guardbench.testrun.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.ExecutionContext;
import com.guardbench.testrun.application.port.out.LoadExecutionContextPort;

@Repository
class ExecutionContextQueryAdapter implements LoadExecutionContextPort {

    private final TestCaseSnapshotJpaRepository snapshotRepository;
    private final TestRunJpaRepository testRunRepository;

    ExecutionContextQueryAdapter(
            TestCaseSnapshotJpaRepository snapshotRepository,
            TestRunJpaRepository testRunRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.testRunRepository = testRunRepository;
    }

    @Override
    public Optional<ExecutionContext> load(long snapshotId, String targetType) {
        return snapshotRepository.findById(snapshotId)
                .flatMap(snapshot -> testRunRepository.findById(snapshot.testRunId)
                        .map(testRun -> toExecutionContext(snapshot, testRun, targetType)));
    }

    private static ExecutionContext toExecutionContext(
            TestCaseSnapshotEntity snapshot,
            TestRunEntity testRun,
            String targetType
    ) {
        String guardrailId = testRun.baselineGuardrailId;
        String version = resolveVersion(testRun, targetType);
        return new ExecutionContext(guardrailId, version, snapshot.input, testRun.id);
    }

    private static String resolveVersion(TestRunEntity testRun, String targetType) {
        return switch (targetType) {
            case "BASELINE" -> testRun.baselineVersion;
            case "CANDIDATE" -> testRun.candidateResolvedVersion;
            default -> throw new IllegalArgumentException("unknown target type: " + targetType);
        };
    }
}

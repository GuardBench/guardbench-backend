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
    public Optional<ExecutionContext> load(long snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .flatMap(snapshot -> testRunRepository.findById(snapshot.testRunId)
                        .map(testRun -> toExecutionContext(snapshot, testRun)));
    }

    private static ExecutionContext toExecutionContext(
            TestCaseSnapshotEntity snapshot,
            TestRunEntity testRun
    ) {
        return new ExecutionContext(
                testRun.targetReferenceId,
                snapshot.input,
                testRun.id,
                testRun.evaluatorReferenceId);
    }
}

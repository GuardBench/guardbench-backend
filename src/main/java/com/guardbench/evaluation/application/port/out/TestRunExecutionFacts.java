package com.guardbench.evaluation.application.port.out;

import java.util.List;
import java.util.Objects;

/**
 * Evaluation Context가 소유하는 TestRun 실행 사실의 불변 값이다.
 *
 * <p>TestRun Domain 타입을 직접 사용하지 않고 스칼라 값 기반으로 표현한다.
 */
public record TestRunExecutionFacts(
        long testRunId,
        String testRunStatus,
        int testCaseCount,
        long successfulExecutionPairCount,
        List<SnapshotExecutionFact> snapshotFacts
) {

    public TestRunExecutionFacts {
        Objects.requireNonNull(testRunStatus, "testRunStatus must not be null");
        Objects.requireNonNull(snapshotFacts, "snapshotFacts must not be null");
        if (testCaseCount <= 0) {
            throw new IllegalArgumentException("testCaseCount must be positive");
        }
    }

    /**
     * 개별 Snapshot의 Baseline/Candidate 실행 결과 사실이다.
     *
     * @param snapshotId TestCaseSnapshot scalar ID
     * @param expectedActionCode expected action code (e.g. "ALLOW", "BLOCK")
     * @param baselineActionCode baseline actual action code, null if not available
     * @param candidateActionCode candidate actual action code, null if not available
     * @param baselineSucceeded baseline execution succeeded
     * @param candidateSucceeded candidate execution succeeded
     */
    public record SnapshotExecutionFact(
            long snapshotId,
            String expectedActionCode,
            String baselineActionCode,
            String candidateActionCode,
            boolean baselineSucceeded,
            boolean candidateSucceeded
    ) {
        public SnapshotExecutionFact {
            Objects.requireNonNull(expectedActionCode, "expectedActionCode must not be null");
        }
    }
}

package com.guardbench.testrun.application;

import java.util.List;
import java.util.Objects;

/**
 * TestRun 최종화에 필요한 실행 사실을 스칼라 값으로 표현하는 Application 계약이다.
 *
 * <p>ADR 0006에 따라 공급 Context(testrun)의 Application 경계가 소비 Context(evaluation)의
 * Integration Adapter에 노출하는 공개 API 반환 타입이다.
 * Domain 타입을 직접 노출하지 않고 스칼라/code 기반으로 표현한다.
 */
public record TestRunFinalizationFacts(
        long testRunId,
        String testRunStatus,
        int testCaseCount,
        long successfulExecutionPairCount,
        List<SnapshotExecutionFact> snapshotFacts
) {

    public TestRunFinalizationFacts {
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

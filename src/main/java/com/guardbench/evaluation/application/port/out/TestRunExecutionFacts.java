package com.guardbench.evaluation.application.port.out;

import java.util.List;
import java.util.Objects;

/**
 * Evaluation Context가 소유하는 TestRun 실행 사실의 불변 값이다.
 *
 * <p>TestRun Domain 타입을 직접 사용하지 않고 스칼라 값 기반으로 표현한다.
 *
 * <p>ADR 0005: 최종화는 모든 pair가 terminal일 때만 가능하므로
 * target별 terminal 여부와 상태 code를 보존한다.
 */
public record TestRunExecutionFacts(
        long testRunId,
        String testRunStatus,
        int testCaseCount,
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
     * 개별 Snapshot의 Baseline/Candidate 실행 사실이다.
     *
     * @param snapshotId TestCaseSnapshot scalar ID
     * @param expectedActionCode expected action code (e.g. "ALLOW", "BLOCK")
     * @param baseline baseline 실행 사실
     * @param candidate candidate 실행 사실
     */
    public record SnapshotExecutionFact(
            long snapshotId,
            String expectedActionCode,
            TargetExecutionFact execution
    ) {
        public SnapshotExecutionFact {
            Objects.requireNonNull(expectedActionCode, "expectedActionCode must not be null");
            Objects.requireNonNull(execution, "execution must not be null");
        }

        public boolean terminal() {
            return execution.terminal();
        }

        public boolean succeeded() {
            return execution.succeeded();
        }
    }

    /**
     * 하나의 target 실행 사실이다.
     *
     * @param terminal 실행 결과가 terminal 상태로 확정되었는지
     * @param statusCode 실행 상태 code(SUCCEEDED, FAILED, TIMED_OUT, NOT_STARTED), 실행 결과가 아직 없으면 null
     * @param actionCode 성공 실행에서 관측된 action code, 그 외에는 null
     */
    public record TargetExecutionFact(
            boolean terminal,
            String statusCode,
            String actionCode
    ) {
        private static final String SUCCEEDED = "SUCCEEDED";

        public TargetExecutionFact {
            if (terminal && statusCode == null) {
                throw new IllegalArgumentException("terminal execution must have a status code");
            }
            if (statusCode == null && actionCode != null) {
                throw new IllegalArgumentException("action code requires a status code");
            }
        }

        /** 실행이 성공으로 확정되었는지 여부다. */
        public boolean succeeded() {
            return terminal && SUCCEEDED.equals(statusCode);
        }

        /** 실행 결과가 아직 저장되지 않은 target이다. */
        public static TargetExecutionFact notExecuted() {
            return new TargetExecutionFact(false, null, null);
        }
    }
}

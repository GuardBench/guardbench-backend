package com.guardbench.testrun.application;

import java.util.List;
import java.util.Objects;

import com.guardbench.testrun.domain.QualityGatePolicy;

/**
 * TestRun 최종화에 필요한 실행 사실을 스칼라 값으로 표현하는 Application 계약이다.
 *
 * <p>ADR 0006에 따라 공급 Context(testrun)의 Application 경계가 소비 Context(evaluation)의
 * Integration Adapter에 노출하는 공개 API 반환 타입이다.
 * Domain 타입을 직접 노출하지 않고 스칼라/code 기반으로 표현한다.
 *
 * <p>ADR 0005: 소비 Context가 모든 Snapshot 실행의 terminal 여부를 판단해야 조기 최종화를 막을 수 있으므로
 * Snapshot별 terminal 여부와 상태 code 및 Evaluator verdict를 유실 없이 전달한다.
 */
public record TestRunFinalizationFacts(
        long testRunId,
        String testRunStatus,
        int testCaseCount,
        String evaluatorReference,
        double assertionPassRateThreshold,
        double executionSuccessRateThreshold,
        List<SnapshotExecutionFact> snapshotFacts
) {

    public TestRunFinalizationFacts {
        Objects.requireNonNull(testRunStatus, "testRunStatus must not be null");
        Objects.requireNonNull(evaluatorReference, "evaluatorReference must not be null");
        Objects.requireNonNull(snapshotFacts, "snapshotFacts must not be null");
        if (testCaseCount <= 0) {
            throw new IllegalArgumentException("testCaseCount must be positive");
        }
        new QualityGatePolicy(assertionPassRateThreshold, executionSuccessRateThreshold);
    }

    /**
     * 개별 Snapshot의 단일 Target 실행 사실이다.
     *
     * @param snapshotId TestCaseSnapshot scalar ID
     * @param expectedActionCode expected action code (e.g. "ALLOW", "BLOCK")
     * @param execution target 실행 사실
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
    }

    /**
     * 하나의 Snapshot pipeline 실행 사실이다.
     *
     * @param terminal 실행 결과가 terminal 상태로 확정되었는지
     * @param statusCode 실행 상태 code(SUCCEEDED, FAILED, TIMED_OUT, NOT_STARTED), 실행 결과가 아직 없으면 null
     * @param actionCode 성공 실행에서 Evaluator가 만든 verdict code, 그 외에는 null
     */
    public record TargetExecutionFact(
            boolean terminal,
            String statusCode,
            String actionCode
    ) {
        public TargetExecutionFact {
            if (terminal && statusCode == null) {
                throw new IllegalArgumentException("terminal execution must have a status code");
            }
            if (statusCode == null && actionCode != null) {
                throw new IllegalArgumentException("action code requires a status code");
            }
        }

        /** 실행 결과가 아직 저장되지 않은 target이다. */
        public static TargetExecutionFact notExecuted() {
            return new TargetExecutionFact(false, null, null);
        }

        public String evaluatorVerdictCode() {
            return actionCode;
        }
    }
}

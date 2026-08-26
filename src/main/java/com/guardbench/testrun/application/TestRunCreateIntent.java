package com.guardbench.testrun.application;

/**
 * Idempotency 판정에 사용하는 TestRun 생성 요청의 정규화된 의도다.
 *
 * <p>ADR 0008에 따라 fingerprint는 이 값만으로 계산하며 raw JSON, Snapshot, materialized version,
 * 실행 시각과 Outbox event는 포함하지 않는다.
 */
public record TestRunCreateIntent(
        long testSuiteId,
        String baselineGuardrailId,
        String baselineVersion,
        String candidateGuardrailId,
        String candidateSource
) {
}

package com.guardbench.testrun.presentation.dto;

/**
 * Snapshot의 단일 Target 처리가 터미널 상태가 되면 처리 완료 한 건으로 계산한 진행률이다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestRunProgressRes</a>
 */
public record TestRunProgressRes(long processedTestCaseCount, double percent) {
}

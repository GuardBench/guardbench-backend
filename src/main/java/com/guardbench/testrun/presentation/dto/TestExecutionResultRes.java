package com.guardbench.testrun.presentation.dto;

/**
 * {@code SUCCEEDED}이면 {@code actualAction}이 있고 {@code error}는 {@code null}이다. 그 밖의
 * 상태에서는 {@code actualAction}이 {@code null}이며 {@code FAILED}와 {@code TIMED_OUT}은 안전한
 * {@code error}를 제공할 수 있다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestExecutionResultRes</a>
 */
public record TestExecutionResultRes(
        String status,
        String actualAction,
        ExecutionErrorDetailRes error) {
}

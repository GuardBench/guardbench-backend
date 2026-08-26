package com.guardbench.testrun.presentation.dto;

/**
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestRunTargetsRes</a>
 */
public record TestRunTargetsRes(
        BaselineExecutionTargetRes baseline,
        CandidateExecutionTargetRes candidate) {
}

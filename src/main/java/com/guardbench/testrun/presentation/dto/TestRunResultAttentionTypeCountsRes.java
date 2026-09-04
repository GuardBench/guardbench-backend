package com.guardbench.testrun.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TestRunResultAttentionTypeCountsRes(
        @JsonProperty("FALSE_NEGATIVE") long falseNegative,
        @JsonProperty("FALSE_POSITIVE") long falsePositive,
        @JsonProperty("EXECUTION_FAILED") long executionFailed,
        @JsonProperty("TIMED_OUT") long timedOut,
        @JsonProperty("NOT_STARTED") long notStarted) {
}

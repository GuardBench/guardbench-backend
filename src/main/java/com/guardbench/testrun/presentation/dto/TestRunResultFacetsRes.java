package com.guardbench.testrun.presentation.dto;

public record TestRunResultFacetsRes(
        long allResults,
        long attentionTotal,
        TestRunResultAttentionTypeCountsRes attentionTypes) {
}

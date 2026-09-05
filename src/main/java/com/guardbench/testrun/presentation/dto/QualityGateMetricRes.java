package com.guardbench.testrun.presentation.dto;

/** Quality Gate 판정에 사용된 단일 지표의 현재 값, 기준, 통과 여부다. */
public record QualityGateMetricRes(
        double value,
        double threshold,
        boolean passed) {
}

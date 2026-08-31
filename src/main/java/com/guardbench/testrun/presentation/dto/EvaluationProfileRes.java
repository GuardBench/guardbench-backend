package com.guardbench.testrun.presentation.dto;

import java.util.List;

/** TestRun 접수 시 고정된 inline 평가 정책 snapshot이다. */
public record EvaluationProfileRes(List<String> checks, String strictness) {
}

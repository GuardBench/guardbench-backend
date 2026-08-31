package com.guardbench.testrun.presentation.dto;

/** TestRun에 고정된 Target의 공개 참조다. */
public record TargetReferenceRes(String referenceId, String type, String identifier, String revision, String model) {
}

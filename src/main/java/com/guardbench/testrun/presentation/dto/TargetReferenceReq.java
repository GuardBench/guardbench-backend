package com.guardbench.testrun.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.guardbench.testrun.presentation.validation.ValidTargetReference;

/** TestRun이 실행할 단일 Target을 표현하는 요청 계약이다. */
@ValidTargetReference
public record TargetReferenceReq(
        @NotBlank(message = "target.type은 필수입니다.")
        @Pattern(regexp = "^HTTP_ENDPOINT$", message = "target.type은 HTTP_ENDPOINT만 지원합니다.") String type,
        @NotBlank(message = "target.identifier는 필수입니다.") String identifier,
        String revision,
        @NotBlank(message = "target.model은 필수입니다.") String model
) {
}

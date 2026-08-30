package com.guardbench.testrun.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** TestRun이 실행할 단일 Target을 표현하는 요청 계약이다. */
public record TargetReferenceReq(
        @NotBlank(message = "target.type은 필수입니다.")
        @Pattern(regexp = "^BEDROCK_GUARDRAIL$", message = "지원하지 않는 target.type입니다.") String type,
        @NotBlank(message = "target.identifier는 필수입니다.") String identifier,
        @NotBlank(message = "target.revision은 필수입니다.")
        @Pattern(
                regexp = "^(DRAFT|[1-9][0-9]{0,7})$",
                message = "target.revision은 DRAFT 또는 유효한 숫자 버전이어야 합니다.") String revision
) {
}

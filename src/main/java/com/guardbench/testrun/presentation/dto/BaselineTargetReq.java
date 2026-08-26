package com.guardbench.testrun.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DRAFT가 아닌 불변 numbered Guardrail version을 표현하는 baseline 대상 요청이다.
 */
public record BaselineTargetReq(
        @NotBlank(message = "baseline.guardrailId는 필수입니다.") String guardrailId,
        @NotBlank(message = "baseline.version은 필수입니다.")
        @Pattern(regexp = "^[0-9]+$", message = "baseline.version은 숫자로만 구성되어야 합니다.") String version
) {
}

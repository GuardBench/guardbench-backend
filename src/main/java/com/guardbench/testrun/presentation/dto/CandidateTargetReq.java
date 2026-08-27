package com.guardbench.testrun.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PREPARING 단계에서 numbered version으로 materialize할 Guardrail DRAFT를 표현하는 candidate 대상
 * 요청이다. MVP에서는 {@code DRAFT}만 허용한다.
 */
public record CandidateTargetReq(
        @NotBlank(message = "candidate.guardrailId는 필수입니다.") String guardrailId,
        @NotBlank(message = "candidate.source는 필수입니다.")
        @Pattern(regexp = "^DRAFT$", message = "candidate.source는 DRAFT만 허용됩니다.") String source
) {
}

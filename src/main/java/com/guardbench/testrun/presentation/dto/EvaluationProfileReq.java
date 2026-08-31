package com.guardbench.testrun.presentation.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import org.hibernate.validator.constraints.UniqueElements;

import com.guardbench.testrun.domain.EvaluationProfile;

/** 사용자가 inline으로 요청하는 평가 목적이다. Evaluator/provider 정보는 포함하지 않는다. */
public record EvaluationProfileReq(
        @NotEmpty(message = "evaluationProfile.checks는 하나 이상 필요합니다.")
        @UniqueElements(message = "evaluationProfile.checks에는 중복 값이 있을 수 없습니다.")
        List<@Pattern(regexp = "PROMPT_INJECTION|PII_LEAKAGE|HARMFUL_CONTENT",
                message = "지원하지 않는 evaluationProfile.checks입니다.") String> checks,
        @NotBlank(message = "evaluationProfile.strictness는 필수입니다.")
        @Pattern(regexp = "RELAXED|STANDARD|STRICT", message = "지원하지 않는 evaluationProfile.strictness입니다.")
        String strictness) {
    public EvaluationProfile toDomain() { return new EvaluationProfile(checks, strictness); }
}

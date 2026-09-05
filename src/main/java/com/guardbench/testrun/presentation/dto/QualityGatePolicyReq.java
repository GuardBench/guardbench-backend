package com.guardbench.testrun.presentation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record QualityGatePolicyReq(
        @NotNull(message = "assertionPassRateThreshold는 필수입니다.")
        @DecimalMin(value = "0.0", message = "assertionPassRateThreshold는 0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "assertionPassRateThreshold는 1 이하여야 합니다.")
        Double assertionPassRateThreshold,
        @NotNull(message = "executionSuccessRateThreshold는 필수입니다.")
        @DecimalMin(value = "0.0", message = "executionSuccessRateThreshold는 0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "executionSuccessRateThreshold는 1 이하여야 합니다.")
        Double executionSuccessRateThreshold) {
}

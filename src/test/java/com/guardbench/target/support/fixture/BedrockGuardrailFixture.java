package com.guardbench.target.support.fixture;

import com.guardbench.testrun.application.port.out.TargetExecutionRequest;
import com.guardbench.testrun.domain.TargetReference;

import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentPolicyAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailPiiEntityFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailSensitiveInformationPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailSensitiveInformationPolicyAssessment;

public final class BedrockGuardrailFixture {
    private static final String INPUT = "test input";

    private BedrockGuardrailFixture() {
    }

    public static TargetExecutionRequest executionRequest() {
        return new TargetExecutionRequest(new TargetReference("target-ref"), INPUT);
    }

    public static ApplyGuardrailResponse noInterventionResponse() {
        return ApplyGuardrailResponse.builder().action(GuardrailAction.NONE).build();
    }

    public static ApplyGuardrailResponse responseWithoutAction() {
        return ApplyGuardrailResponse.builder().build();
    }

    public static ApplyGuardrailResponse intervenedResponse(GuardrailAssessment assessment) {
        return ApplyGuardrailResponse.builder()
                .action(GuardrailAction.GUARDRAIL_INTERVENED)
                .assessments(assessment)
                .build();
    }

    public static GuardrailAssessment blockedContentAssessment() {
        return GuardrailAssessment.builder()
                .contentPolicy(GuardrailContentPolicyAssessment.builder()
                        .filters(GuardrailContentFilter.builder()
                                .action(GuardrailContentPolicyAction.BLOCKED)
                                .build())
                        .build())
                .build();
    }

    public static GuardrailAssessment anonymizedPiiAssessment(GuardrailSensitiveInformationPolicyAction action) {
        return GuardrailAssessment.builder()
                .sensitiveInformationPolicy(GuardrailSensitiveInformationPolicyAssessment.builder()
                        .piiEntities(GuardrailPiiEntityFilter.builder().action(action).build())
                        .build())
                .build();
    }

    public static GuardrailAssessment blockedAndAnonymizedAssessment() {
        return GuardrailAssessment.builder()
                .contentPolicy(blockedContentAssessment().contentPolicy())
                .sensitiveInformationPolicy(anonymizedPiiAssessment(GuardrailSensitiveInformationPolicyAction.ANONYMIZED)
                        .sensitiveInformationPolicy())
                .build();
    }
}

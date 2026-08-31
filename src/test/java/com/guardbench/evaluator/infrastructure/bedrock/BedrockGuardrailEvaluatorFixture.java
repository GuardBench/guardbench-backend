package com.guardbench.evaluator.infrastructure.bedrock;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionRequest;
import com.guardbench.testrun.domain.EvaluatorReference;

import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentPolicyAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailPiiEntityFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailSensitiveInformationPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailSensitiveInformationPolicyAssessment;

final class BedrockGuardrailEvaluatorFixture {
    private static final String RESPONSE = "application response";

    private BedrockGuardrailEvaluatorFixture() {
    }

    static EvaluatorExecutionRequest executionRequest() {
        return new EvaluatorExecutionRequest(new EvaluatorReference("evaluator-ref"), RESPONSE);
    }

    static ApplyGuardrailResponse noInterventionResponse() {
        return ApplyGuardrailResponse.builder().action(GuardrailAction.NONE).build();
    }

    static ApplyGuardrailResponse responseWithoutAction() {
        return ApplyGuardrailResponse.builder().build();
    }

    static ApplyGuardrailResponse intervenedResponse(GuardrailAssessment assessment) {
        return ApplyGuardrailResponse.builder()
                .action(GuardrailAction.GUARDRAIL_INTERVENED)
                .assessments(assessment)
                .build();
    }

    static GuardrailAssessment blockedContentAssessment() {
        return GuardrailAssessment.builder()
                .contentPolicy(GuardrailContentPolicyAssessment.builder()
                        .filters(GuardrailContentFilter.builder()
                                .action(GuardrailContentPolicyAction.BLOCKED)
                                .build())
                        .build())
                .build();
    }

    static GuardrailAssessment anonymizedPiiAssessment(GuardrailSensitiveInformationPolicyAction action) {
        return GuardrailAssessment.builder()
                .sensitiveInformationPolicy(GuardrailSensitiveInformationPolicyAssessment.builder()
                        .piiEntities(GuardrailPiiEntityFilter.builder().action(action).build())
                        .build())
                .build();
    }

    static GuardrailAssessment blockedAndAnonymizedAssessment() {
        return GuardrailAssessment.builder()
                .contentPolicy(blockedContentAssessment().contentPolicy())
                .sensitiveInformationPolicy(anonymizedPiiAssessment(GuardrailSensitiveInformationPolicyAction.ANONYMIZED)
                        .sensitiveInformationPolicy())
                .build();
    }
}

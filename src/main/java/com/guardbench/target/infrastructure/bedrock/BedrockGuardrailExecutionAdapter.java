package com.guardbench.target.infrastructure.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetExecutionRequest;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailOutputScope;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock;

/**
 * Bedrock Runtime {@code ApplyGuardrail} 호출을 TestRun이 소유한 실행 Port로 변환한다.
 */
public final class BedrockGuardrailExecutionAdapter implements TargetExecutionPort {

    private static final String NONE_ACTION = "NONE";
    private static final String INTERVENED_ACTION = "GUARDRAIL_INTERVENED";
    private static final String ALLOW_ACTION = "ALLOW";
    private static final String BLOCK_ACTION = "BLOCK";
    private static final String BLOCKED_POLICY_ACTION = "BLOCKED";
    private static final String ANONYMIZED_POLICY_ACTION = "ANONYMIZED";
    private static final String NO_POLICY_ACTION = "NONE";

    private final BedrockRuntimeClient client;
    private final BedrockGuardrailTargetStore targetStore;

    public BedrockGuardrailExecutionAdapter(
            BedrockRuntimeClient client,
            BedrockGuardrailTargetStore targetStore
    ) {
        this.client = Objects.requireNonNull(client, "BedrockRuntimeClient must not be null");
        this.targetStore = Objects.requireNonNull(targetStore, "target store must not be null");
    }

    @Override
    public TargetExecutionResult execute(TargetExecutionRequest request) {
        Objects.requireNonNull(request, "execution request must not be null");
        BedrockGuardrailTargetStore.BedrockGuardrailTarget target = targetStore
                .findByReference(request.targetReference().value())
                .orElse(null);
        if (target == null) {
            return TargetExecutionResult.failed(TargetFailureCode.TARGET_NOT_FOUND);
        }

        try {
            ApplyGuardrailResponse response = client.applyGuardrail(toSdkRequest(request, target));
            return normalizeResponse(response);
        } catch (SdkException exception) {
            return TargetExecutionResult.failed(BedrockGuardrailFailureCodeMapper.map(exception));
        }
    }

    private static TargetExecutionResult normalizeResponse(ApplyGuardrailResponse response) {
        if (response == null || response.actionAsString() == null || response.actionAsString().isBlank()) {
            return invalidProviderResponse();
        }

        return switch (response.actionAsString()) {
            case NONE_ACTION -> TargetExecutionResult.succeeded(ALLOW_ACTION);
            case INTERVENED_ACTION -> normalizeIntervention(response.assessments());
            default -> invalidProviderResponse();
        };
    }

    /**
     * {@code GUARDRAIL_INTERVENED} alone is ambiguous: AWS uses it for both hard blocks and anonymization.
     * Only allowlisted structured policy actions are inspected; raw matches, regexes, PII, outputs, and reasons
     * never cross the Port boundary.
     */
    private static TargetExecutionResult normalizeIntervention(List<GuardrailAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) {
            return invalidProviderResponse();
        }

        List<String> policyActions = new ArrayList<>();
        for (GuardrailAssessment assessment : assessments) {
            collectPolicyActions(assessment, policyActions);
        }

        boolean blocked = false;
        boolean anonymized = false;
        for (String policyAction : policyActions) {
            if (policyAction == null) {
                return invalidProviderResponse();
            }

            switch (policyAction) {
                case BLOCKED_POLICY_ACTION -> blocked = true;
                case ANONYMIZED_POLICY_ACTION -> anonymized = true;
                case NO_POLICY_ACTION -> {
                    // A non-detected policy entry does not determine the intervention outcome.
                }
                default -> {
                    return invalidProviderResponse();
                }
            }
        }

        if (blocked) {
            return TargetExecutionResult.succeeded(BLOCK_ACTION);
        }
        if (anonymized) {
            return TargetExecutionResult.succeeded(ALLOW_ACTION);
        }
        return invalidProviderResponse();
    }

    private static void collectPolicyActions(GuardrailAssessment assessment, List<String> policyActions) {
        if (assessment == null) {
            policyActions.add(null);
            return;
        }

        var contentPolicy = assessment.contentPolicy();
        if (contentPolicy != null) {
            contentPolicy.filters().forEach(filter -> policyActions.add(filter.actionAsString()));
        }

        var topicPolicy = assessment.topicPolicy();
        if (topicPolicy != null) {
            topicPolicy.topics().forEach(topic -> policyActions.add(topic.actionAsString()));
        }

        var wordPolicy = assessment.wordPolicy();
        if (wordPolicy != null) {
            wordPolicy.customWords().forEach(word -> policyActions.add(word.actionAsString()));
            wordPolicy.managedWordLists().forEach(word -> policyActions.add(word.actionAsString()));
        }

        var sensitiveInformationPolicy = assessment.sensitiveInformationPolicy();
        if (sensitiveInformationPolicy != null) {
            sensitiveInformationPolicy.piiEntities().forEach(entity -> policyActions.add(entity.actionAsString()));
            sensitiveInformationPolicy.regexes().forEach(regex -> policyActions.add(regex.actionAsString()));
        }

        var contextualGroundingPolicy = assessment.contextualGroundingPolicy();
        if (contextualGroundingPolicy != null) {
            contextualGroundingPolicy.filters().forEach(filter -> policyActions.add(filter.actionAsString()));
        }
    }

    private static TargetExecutionResult invalidProviderResponse() {
        return TargetExecutionResult.failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
    }

    private static ApplyGuardrailRequest toSdkRequest(
            TargetExecutionRequest request,
            BedrockGuardrailTargetStore.BedrockGuardrailTarget target
    ) {
        return ApplyGuardrailRequest.builder()
                .guardrailIdentifier(target.guardrailIdentifier())
                .guardrailVersion(target.executableRevision())
                .source(GuardrailContentSource.INPUT)
                .content(GuardrailContentBlock.fromText(GuardrailTextBlock.builder()
                        .text(request.input())
                        .build()))
                .outputScope(GuardrailOutputScope.INTERVENTIONS)
                .build();
    }
}

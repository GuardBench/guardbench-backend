package com.guardbench.evaluator.infrastructure.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionPort;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionRequest;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;
import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailOutputScope;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock;

/** Application response를 Bedrock {@code ApplyGuardrail}로 평가하는 Evaluator adapter다. */
public final class BedrockGuardrailEvaluatorAdapter implements EvaluatorExecutionPort {

    private static final String NONE_ACTION = "NONE";
    private static final String INTERVENED_ACTION = "GUARDRAIL_INTERVENED";
    private static final String ALLOW_ACTION = "ALLOW";
    private static final String BLOCK_ACTION = "BLOCK";
    private static final String BLOCKED_POLICY_ACTION = "BLOCKED";
    private static final String ANONYMIZED_POLICY_ACTION = "ANONYMIZED";
    private static final String NO_POLICY_ACTION = "NONE";

    private final BedrockRuntimeClient client;
    private final BedrockGuardrailEvaluatorStore evaluatorStore;

    public BedrockGuardrailEvaluatorAdapter(
            BedrockRuntimeClient client,
            BedrockGuardrailEvaluatorStore evaluatorStore
    ) {
        this.client = Objects.requireNonNull(client, "BedrockRuntimeClient must not be null");
        this.evaluatorStore = Objects.requireNonNull(evaluatorStore, "evaluator store must not be null");
    }

    @Override
    public EvaluatorExecutionResult evaluate(EvaluatorExecutionRequest request) {
        Objects.requireNonNull(request, "evaluator request must not be null");
        BedrockGuardrailEvaluatorStore.BedrockGuardrailEvaluator evaluator = evaluatorStore
                .findByReference(request.evaluatorReference().value())
                .orElse(null);
        if (evaluator == null) {
            return EvaluatorExecutionResult.failed(EvaluatorFailureCode.EVALUATOR_NOT_FOUND);
        }

        try {
            ApplyGuardrailResponse response = client.applyGuardrail(toSdkRequest(request, evaluator));
            return normalizeResponse(response);
        } catch (SdkException exception) {
            return EvaluatorExecutionResult.failed(BedrockGuardrailFailureCodeMapper.map(exception));
        }
    }

    private static EvaluatorExecutionResult normalizeResponse(ApplyGuardrailResponse response) {
        if (response == null || response.actionAsString() == null || response.actionAsString().isBlank()) {
            return invalidProviderResponse();
        }

        return switch (response.actionAsString()) {
            case NONE_ACTION -> EvaluatorExecutionResult.succeeded(ALLOW_ACTION);
            case INTERVENED_ACTION -> normalizeIntervention(response.assessments());
            default -> invalidProviderResponse();
        };
    }

    /** Structured assessment만 사용해 intervention을 공통 verdict로 정규화한다. */
    private static EvaluatorExecutionResult normalizeIntervention(List<GuardrailAssessment> assessments) {
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
            return EvaluatorExecutionResult.succeeded(BLOCK_ACTION);
        }
        if (anonymized) {
            return EvaluatorExecutionResult.succeeded(ALLOW_ACTION);
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

    private static EvaluatorExecutionResult invalidProviderResponse() {
        return EvaluatorExecutionResult.failed(EvaluatorFailureCode.PROVIDER_RESPONSE_INVALID);
    }

    private static ApplyGuardrailRequest toSdkRequest(
            EvaluatorExecutionRequest request,
            BedrockGuardrailEvaluatorStore.BedrockGuardrailEvaluator evaluator
    ) {
        return ApplyGuardrailRequest.builder()
                .guardrailIdentifier(evaluator.guardrailIdentifier())
                .guardrailVersion(evaluator.revision())
                .source(GuardrailContentSource.OUTPUT)
                .content(GuardrailContentBlock.fromText(GuardrailTextBlock.builder()
                        .text(request.applicationResponse())
                        .build()))
                .outputScope(GuardrailOutputScope.INTERVENTIONS)
                .build();
    }
}

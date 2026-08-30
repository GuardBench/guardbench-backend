package com.guardbench.target.infrastructure.bedrock;

import java.util.Objects;

import com.guardbench.testrun.application.port.out.TargetFailureCode;
import com.guardbench.testrun.application.port.out.TargetPreparationPort;
import com.guardbench.testrun.application.port.out.TargetPreparationRequest;
import com.guardbench.testrun.application.port.out.TargetProviderException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.CreateGuardrailVersionRequest;
import software.amazon.awssdk.services.bedrock.model.CreateGuardrailVersionResponse;

/**
 * Bedrock {@code CreateGuardrailVersion} 호출을 TestRun이 소유한 materialization Port로 변환한다.
 */
public final class BedrockGuardrailPreparationAdapter implements TargetPreparationPort {

    private final BedrockClient client;
    private final BedrockGuardrailTargetStore targetStore;

    public BedrockGuardrailPreparationAdapter(BedrockClient client, BedrockGuardrailTargetStore targetStore) {
        this.client = Objects.requireNonNull(client, "BedrockClient must not be null");
        this.targetStore = Objects.requireNonNull(targetStore, "target store must not be null");
    }

    @Override
    public void prepare(TargetPreparationRequest request) {
        Objects.requireNonNull(request, "preparation request must not be null");

        BedrockGuardrailTargetStore.BedrockGuardrailTarget target = targetStore
                .findByReference(request.targetReference().value())
                .orElseThrow(() -> new TargetProviderException(TargetFailureCode.TARGET_NOT_FOUND));
        if (target.resolvedRevision() != null) {
            return;
        }

        try {
            CreateGuardrailVersionResponse response = client.createGuardrailVersion(toSdkRequest(target, request));
            targetStore.saveResolvedRevision(request.targetReference().value(), toResolvedRevision(target, response));
        } catch (SdkException exception) {
            throw new TargetProviderException(BedrockGuardrailFailureCodeMapper.map(exception));
        }
    }

    private static CreateGuardrailVersionRequest toSdkRequest(
            BedrockGuardrailTargetStore.BedrockGuardrailTarget target,
            TargetPreparationRequest request
    ) {
        return CreateGuardrailVersionRequest.builder()
                .guardrailIdentifier(target.guardrailIdentifier())
                .clientRequestToken(request.idempotencyToken())
                .build();
    }

    private static String toResolvedRevision(
            BedrockGuardrailTargetStore.BedrockGuardrailTarget target,
            CreateGuardrailVersionResponse response
    ) {
        if (response == null) {
            throw new TargetProviderException(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
        }
        if (!target.guardrailIdentifier().equals(response.guardrailId())
                || response.version() == null
                || !response.version().matches("[1-9][0-9]{0,7}")) {
            throw new TargetProviderException(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
        }
        return response.version();
    }
}

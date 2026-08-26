package com.guardbench.guardrail.infrastructure.bedrock;

import java.util.Objects;

import com.guardbench.testrun.application.port.out.GuardrailFailureCode;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationPort;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationRequest;
import com.guardbench.testrun.application.port.out.GuardrailMaterializedVersion;
import com.guardbench.testrun.application.port.out.GuardrailProviderException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.CreateGuardrailVersionRequest;
import software.amazon.awssdk.services.bedrock.model.CreateGuardrailVersionResponse;

/**
 * Bedrock {@code CreateGuardrailVersion} 호출을 TestRun이 소유한 materialization Port로 변환한다.
 */
public final class BedrockGuardrailMaterializationAdapter implements GuardrailMaterializationPort {

    private final BedrockClient client;

    public BedrockGuardrailMaterializationAdapter(BedrockClient client) {
        this.client = Objects.requireNonNull(client, "BedrockClient must not be null");
    }

    @Override
    public GuardrailMaterializedVersion materialize(GuardrailMaterializationRequest request) {
        Objects.requireNonNull(request, "materialization request must not be null");

        try {
            CreateGuardrailVersionResponse response = client.createGuardrailVersion(toSdkRequest(request));
            return toMaterializedVersion(response);
        } catch (SdkException exception) {
            throw new GuardrailProviderException(BedrockGuardrailFailureCodeMapper.map(exception));
        }
    }

    private static CreateGuardrailVersionRequest toSdkRequest(GuardrailMaterializationRequest request) {
        return CreateGuardrailVersionRequest.builder()
                .guardrailIdentifier(request.guardrailIdentifier())
                .clientRequestToken(request.clientRequestToken())
                .build();
    }

    private static GuardrailMaterializedVersion toMaterializedVersion(CreateGuardrailVersionResponse response) {
        if (response == null) {
            throw new GuardrailProviderException(GuardrailFailureCode.PROVIDER_RESPONSE_INVALID);
        }
        try {
            return new GuardrailMaterializedVersion(response.guardrailId(), response.version());
        } catch (IllegalArgumentException exception) {
            throw new GuardrailProviderException(GuardrailFailureCode.PROVIDER_RESPONSE_INVALID);
        }
    }
}

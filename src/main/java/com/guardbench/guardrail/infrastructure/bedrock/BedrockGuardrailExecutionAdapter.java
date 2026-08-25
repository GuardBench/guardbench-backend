package com.guardbench.guardrail.infrastructure.bedrock;

import java.util.Objects;

import com.guardbench.testrun.application.port.out.GuardrailExecutionPort;
import com.guardbench.testrun.application.port.out.GuardrailExecutionRequest;
import com.guardbench.testrun.application.port.out.GuardrailExecutionResult;
import com.guardbench.testrun.application.port.out.GuardrailFailureCode;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailOutputScope;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock;

/**
 * Bedrock Runtime {@code ApplyGuardrail} 호출을 TestRun이 소유한 실행 Port로 변환한다.
 */
public final class BedrockGuardrailExecutionAdapter implements GuardrailExecutionPort {

    private final BedrockRuntimeClient client;

    public BedrockGuardrailExecutionAdapter(BedrockRuntimeClient client) {
        this.client = Objects.requireNonNull(client, "BedrockRuntimeClient must not be null");
    }

    @Override
    public GuardrailExecutionResult execute(GuardrailExecutionRequest request) {
        Objects.requireNonNull(request, "execution request must not be null");

        try {
            ApplyGuardrailResponse response = client.applyGuardrail(toSdkRequest(request));
            if (response == null || response.actionAsString() == null || response.actionAsString().isBlank()) {
                return GuardrailExecutionResult.failed(GuardrailFailureCode.PROVIDER_RESPONSE_INVALID);
            }
            return GuardrailExecutionResult.succeeded(response.actionAsString());
        } catch (SdkException exception) {
            return GuardrailExecutionResult.failed(BedrockGuardrailFailureCodeMapper.map(exception));
        }
    }

    private static ApplyGuardrailRequest toSdkRequest(GuardrailExecutionRequest request) {
        return ApplyGuardrailRequest.builder()
                .guardrailIdentifier(request.guardrailIdentifier())
                .guardrailVersion(request.guardrailVersion())
                .source(GuardrailContentSource.INPUT)
                .content(GuardrailContentBlock.fromText(GuardrailTextBlock.builder()
                        .text(request.input())
                        .build()))
                .outputScope(GuardrailOutputScope.INTERVENTIONS)
                .build();
    }
}

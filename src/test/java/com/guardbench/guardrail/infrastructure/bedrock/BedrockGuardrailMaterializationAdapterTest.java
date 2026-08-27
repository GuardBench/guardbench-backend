package com.guardbench.guardrail.infrastructure.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardbench.testrun.application.port.out.GuardrailFailureCode;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationRequest;
import com.guardbench.testrun.application.port.out.GuardrailMaterializedVersion;
import com.guardbench.testrun.application.port.out.GuardrailProviderException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.ConflictException;
import software.amazon.awssdk.services.bedrock.model.CreateGuardrailVersionRequest;
import software.amazon.awssdk.services.bedrock.model.CreateGuardrailVersionResponse;

@ExtendWith(MockitoExtension.class)
class BedrockGuardrailMaterializationAdapterTest {

    @Mock
    private BedrockClient client;

    @Captor
    private ArgumentCaptor<CreateGuardrailVersionRequest> requestCaptor;

    @Test
    @DisplayName("CreateGuardrailVersion은 TestRun 기반 clientRequestToken으로 materialized version을 반환한다")
    void materializesGuardrailWithDeterministicClientRequestToken() {
        when(client.createGuardrailVersion(any(CreateGuardrailVersionRequest.class)))
                .thenReturn(CreateGuardrailVersionResponse.builder()
                        .guardrailId("gr-123")
                        .version("7")
                        .build());
        BedrockGuardrailMaterializationAdapter adapter = new BedrockGuardrailMaterializationAdapter(client);

        GuardrailMaterializedVersion result = adapter.materialize(new GuardrailMaterializationRequest("gr-123", 42));

        verify(client).createGuardrailVersion(requestCaptor.capture());
        assertEquals("gr-123", requestCaptor.getValue().guardrailIdentifier());
        assertEquals("guardbench-test-run-42", requestCaptor.getValue().clientRequestToken());
        assertEquals("gr-123", result.guardrailIdentifier());
        assertEquals("7", result.version());
    }

    @Test
    @DisplayName("materialization ConflictException은 TARGET_CONFIGURATION_INVALID로 변환한다")
    void mapsConflictToTargetConfigurationInvalid() {
        when(client.createGuardrailVersion(any(CreateGuardrailVersionRequest.class)))
                .thenThrow(ConflictException.builder().build());
        BedrockGuardrailMaterializationAdapter adapter = new BedrockGuardrailMaterializationAdapter(client);

        GuardrailProviderException exception = assertThrows(GuardrailProviderException.class,
                () -> adapter.materialize(new GuardrailMaterializationRequest("gr-123", 42)));

        assertEquals(GuardrailFailureCode.TARGET_CONFIGURATION_INVALID, exception.failureCode());
    }

    @Test
    @DisplayName("숫자형 version이 아닌 materialization 응답은 PROVIDER_RESPONSE_INVALID로 변환한다")
    void mapsInvalidMaterializedVersionToProviderResponseInvalid() {
        when(client.createGuardrailVersion(any(CreateGuardrailVersionRequest.class)))
                .thenReturn(CreateGuardrailVersionResponse.builder()
                        .guardrailId("gr-123")
                        .version("DRAFT")
                        .build());
        BedrockGuardrailMaterializationAdapter adapter = new BedrockGuardrailMaterializationAdapter(client);

        GuardrailProviderException exception = assertThrows(GuardrailProviderException.class,
                () -> adapter.materialize(new GuardrailMaterializationRequest("gr-123", 42)));

        assertEquals(GuardrailFailureCode.PROVIDER_RESPONSE_INVALID, exception.failureCode());
    }
}

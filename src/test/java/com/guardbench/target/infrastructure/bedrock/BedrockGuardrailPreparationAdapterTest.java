package com.guardbench.target.infrastructure.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.guardbench.testrun.application.port.out.TargetFailureCode;
import com.guardbench.testrun.application.port.out.TargetPreparationRequest;
import com.guardbench.testrun.application.port.out.TargetProviderException;
import com.guardbench.testrun.domain.TargetReference;

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
class BedrockGuardrailPreparationAdapterTest {

    @Mock
    private BedrockClient client;

    @Mock
    private BedrockGuardrailTargetStore targetStore;

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
        BedrockGuardrailPreparationAdapter adapter = adapter();

        adapter.prepare(request());

        verify(client).createGuardrailVersion(requestCaptor.capture());
        assertEquals("gr-123", requestCaptor.getValue().guardrailIdentifier());
        assertEquals("guardbench-test-run-42", requestCaptor.getValue().clientRequestToken());
        verify(targetStore).saveResolvedRevision("target-ref", "7");
    }

    @Test
    @DisplayName("materialization ConflictException은 TARGET_CONFIGURATION_INVALID로 변환한다")
    void mapsConflictToTargetConfigurationInvalid() {
        when(client.createGuardrailVersion(any(CreateGuardrailVersionRequest.class)))
                .thenThrow(ConflictException.builder().build());
        BedrockGuardrailPreparationAdapter adapter = adapter();

        TargetProviderException exception = assertThrows(TargetProviderException.class,
                () -> adapter.prepare(request()));

        assertEquals(TargetFailureCode.TARGET_CONFIGURATION_INVALID, exception.failureCode());
    }

    @Test
    @DisplayName("숫자형 version이 아닌 materialization 응답은 PROVIDER_RESPONSE_INVALID로 변환한다")
    void mapsInvalidMaterializedVersionToProviderResponseInvalid() {
        when(client.createGuardrailVersion(any(CreateGuardrailVersionRequest.class)))
                .thenReturn(CreateGuardrailVersionResponse.builder()
                        .guardrailId("gr-123")
                        .version("DRAFT")
                        .build());
        BedrockGuardrailPreparationAdapter adapter = adapter();

        TargetProviderException exception = assertThrows(TargetProviderException.class,
                () -> adapter.prepare(request()));

        assertEquals(TargetFailureCode.PROVIDER_RESPONSE_INVALID, exception.failureCode());
    }

    private BedrockGuardrailPreparationAdapter adapter() {
        when(targetStore.findByReference("target-ref"))
                .thenReturn(Optional.of(new BedrockGuardrailTargetStore.BedrockGuardrailTarget(
                        "target-ref", "gr-123", "DRAFT", null)));
        return new BedrockGuardrailPreparationAdapter(client, targetStore);
    }

    private TargetPreparationRequest request() {
        return new TargetPreparationRequest(new TargetReference("target-ref"), 42);
    }
}

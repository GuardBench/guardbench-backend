package com.guardbench.target.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.guardbench.testrun.application.port.out.TargetPreparationRequest;
import com.guardbench.testrun.application.port.out.TargetProviderException;
import com.guardbench.testrun.domain.TargetReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpEndpointPreparationAdapterTest {

    @Mock
    private HttpEndpointTargetStore targetStore;

    @Test
    @DisplayName("HTTP Endpoint 준비는 외부 호출 없이 저장된 URL을 검증한다")
    void validatesStoredEndpointWithoutExternalCall() {
        when(targetStore.findByReference("target-ref")).thenReturn(Optional.of(
                new HttpEndpointTargetStore.HttpEndpointTarget("target-ref", "https://example.com/chat")));

        assertDoesNotThrow(() -> new HttpEndpointPreparationAdapter(targetStore)
                .prepare(new TargetPreparationRequest(new TargetReference("target-ref"), 1)));
    }

    @Test
    @DisplayName("등록된 HTTP Endpoint가 없으면 TARGET_NOT_FOUND다")
    void rejectsMissingEndpoint() {
        when(targetStore.findByReference("target-ref")).thenReturn(Optional.empty());

        TargetProviderException exception = assertThrows(TargetProviderException.class, () ->
                new HttpEndpointPreparationAdapter(targetStore)
                        .prepare(new TargetPreparationRequest(new TargetReference("target-ref"), 1)));

        assertEquals(com.guardbench.testrun.application.port.out.TargetFailureCode.TARGET_NOT_FOUND,
                exception.failureCode());
    }
}

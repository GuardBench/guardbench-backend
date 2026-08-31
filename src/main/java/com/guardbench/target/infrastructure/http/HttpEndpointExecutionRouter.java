package com.guardbench.target.infrastructure.http;

import java.util.Objects;

import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetExecutionRequest;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;

/** model 필드의 존재를 OpenAI-compatible 형식 선택자로 사용해 HTTP Adapter를 라우팅한다. */
final class HttpEndpointExecutionRouter implements TargetExecutionPort {

    private final HttpEndpointTargetStore targetStore;
    private final HttpEndpointExecutionAdapter genericAdapter;
    private final OpenAiCompatibleExecutionAdapter openAiAdapter;

    HttpEndpointExecutionRouter(
            HttpEndpointTargetStore targetStore,
            HttpEndpointExecutionAdapter genericAdapter,
            OpenAiCompatibleExecutionAdapter openAiAdapter
    ) {
        this.targetStore = Objects.requireNonNull(targetStore);
        this.genericAdapter = Objects.requireNonNull(genericAdapter);
        this.openAiAdapter = Objects.requireNonNull(openAiAdapter);
    }

    @Override
    public TargetExecutionResult execute(TargetExecutionRequest request) {
        Objects.requireNonNull(request, "execution request must not be null");
        HttpEndpointTargetStore.HttpEndpointTarget target = targetStore
                .findByReference(request.targetReference().value())
                .orElse(null);
        if (target == null) return TargetExecutionResult.failed(TargetFailureCode.TARGET_NOT_FOUND);
        return target.model() == null
                ? genericAdapter.execute(request, target)
                : openAiAdapter.execute(request, target);
    }
}

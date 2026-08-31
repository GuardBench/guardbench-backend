package com.guardbench.target.infrastructure.http;

import java.util.Objects;

import com.guardbench.testrun.application.port.out.TargetFailureCode;
import com.guardbench.testrun.application.port.out.TargetPreparationPort;
import com.guardbench.testrun.application.port.out.TargetPreparationRequest;
import com.guardbench.testrun.application.port.out.TargetProviderException;

/** HTTP Endpoint는 별도의 materialization 없이 저장된 URL의 존재·구문만 확인한다. */
final class HttpEndpointPreparationAdapter implements TargetPreparationPort {

    private final HttpEndpointTargetStore targetStore;

    HttpEndpointPreparationAdapter(HttpEndpointTargetStore targetStore) {
        this.targetStore = Objects.requireNonNull(targetStore);
    }

    @Override
    public void prepare(TargetPreparationRequest request) {
        Objects.requireNonNull(request, "preparation request must not be null");
        HttpEndpointTargetStore.HttpEndpointTarget target = targetStore
                .findByReference(request.targetReference().value())
                .orElseThrow(() -> new TargetProviderException(TargetFailureCode.TARGET_NOT_FOUND));
        try {
            HttpEndpointUrlValidator.parse(target.endpointUrl());
        } catch (IllegalArgumentException exception) {
            throw new TargetProviderException(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
        }
    }
}

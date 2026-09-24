package com.guardbench.target.infrastructure.persistence;

import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.RegisterTargetReferencePort;
import com.guardbench.testrun.application.port.out.TargetRegistration;

/** TargetReference와 provider-specific 설정을 Target 소유 테이블에 저장한다. */
@Repository
class TargetReferencePersistenceAdapter implements RegisterTargetReferencePort {
    private static final String HTTP_ENDPOINT = "HTTP_ENDPOINT";
    private final JdbcTemplate jdbcTemplate;

    TargetReferencePersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public void register(String referenceId, TargetRegistration registration) {
        Objects.requireNonNull(referenceId, "target reference ID must not be null");
        Objects.requireNonNull(registration, "target registration must not be null");
        if (!HTTP_ENDPOINT.equals(registration.typeCode())) {
            throw new IllegalArgumentException("unsupported target type: " + registration.typeCode());
        }
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, ?)",
                referenceId, registration.typeCode());
        registerHttpEndpoint(referenceId, registration);
    }

    private void registerHttpEndpoint(String referenceId, TargetRegistration registration) {
        jdbcTemplate.update("INSERT INTO http_endpoint_target(reference_id, endpoint_url, requested_revision, model) VALUES (?, ?, ?, ?)",
                referenceId, registration.identifier(), registration.revision(), registration.model());
    }

}

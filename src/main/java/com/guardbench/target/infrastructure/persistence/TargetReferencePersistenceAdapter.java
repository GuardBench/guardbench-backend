package com.guardbench.target.infrastructure.persistence;

import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.RegisterTargetReferencePort;
import com.guardbench.testrun.application.port.out.TargetRegistration;
import com.guardbench.testrun.domain.TargetReference;

/** TargetReference와 provider-specific 설정을 Target 소유 테이블에 저장한다. */
@Repository
class TargetReferencePersistenceAdapter implements RegisterTargetReferencePort {
    private static final String BEDROCK_GUARDRAIL = "BEDROCK_GUARDRAIL";
    private static final String HTTP_ENDPOINT = "HTTP_ENDPOINT";
    private final JdbcTemplate jdbcTemplate;

    TargetReferencePersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public void register(TargetReference reference, TargetRegistration registration) {
        Objects.requireNonNull(reference, "target reference must not be null");
        Objects.requireNonNull(registration, "target registration must not be null");
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, ?)",
                reference.value(), registration.typeCode());
        switch (registration.typeCode()) {
            case BEDROCK_GUARDRAIL -> registerBedrockGuardrail(reference, registration);
            case HTTP_ENDPOINT -> registerHttpEndpoint(reference, registration);
            default -> throw new IllegalArgumentException("unsupported target type: " + registration.typeCode());
        }
    }

    private void registerBedrockGuardrail(TargetReference reference, TargetRegistration registration) {
        String resolvedRevision = isDraft(registration.revision()) ? null : registration.revision();
        jdbcTemplate.update("""
                INSERT INTO bedrock_guardrail_target(
                    reference_id, guardrail_identifier, requested_revision, resolved_revision
                ) VALUES (?, ?, ?, ?)
                """, reference.value(), registration.identifier(), registration.revision(), resolvedRevision);
    }

    private void registerHttpEndpoint(TargetReference reference, TargetRegistration registration) {
        jdbcTemplate.update("INSERT INTO http_endpoint_target(reference_id, endpoint_url, requested_revision, model) VALUES (?, ?, ?, ?)",
                reference.value(), registration.identifier(), registration.revision(), registration.model());
    }

    private static boolean isDraft(String revision) {
        if ("DRAFT".equals(revision)) return true;
        if (revision == null || !revision.matches("[1-9][0-9]{0,7}")) {
            throw new IllegalArgumentException("Bedrock Guardrail revision must be DRAFT or a numbered version");
        }
        return false;
    }
}

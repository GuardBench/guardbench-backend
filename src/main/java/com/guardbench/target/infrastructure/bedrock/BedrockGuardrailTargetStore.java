package com.guardbench.target.infrastructure.bedrock;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class BedrockGuardrailTargetStore {

    private final JdbcTemplate jdbcTemplate;

    BedrockGuardrailTargetStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<BedrockGuardrailTarget> findByReference(String referenceId) {
        List<BedrockGuardrailTarget> rows = jdbcTemplate.query(
                """
                SELECT reference_id, guardrail_identifier, requested_revision, resolved_revision
                FROM bedrock_guardrail_target
                WHERE reference_id = ?
                """,
                (resultSet, rowNumber) -> new BedrockGuardrailTarget(
                        resultSet.getString("reference_id"),
                        resultSet.getString("guardrail_identifier"),
                        resultSet.getString("requested_revision"),
                        resultSet.getString("resolved_revision")),
                referenceId);
        return rows.stream().findFirst();
    }

    void saveResolvedRevision(String referenceId, String resolvedRevision) {
        int updated = jdbcTemplate.update(
                "UPDATE bedrock_guardrail_target SET resolved_revision = ? WHERE reference_id = ?",
                resolvedRevision, referenceId);
        if (updated != 1) {
            throw new IllegalStateException("target reference disappeared during preparation");
        }
    }

    record BedrockGuardrailTarget(
            String referenceId,
            String guardrailIdentifier,
            String requestedRevision,
            String resolvedRevision
    ) {
        String executableRevision() {
            if (resolvedRevision == null) {
                throw new IllegalStateException("Bedrock Guardrail target is not prepared");
            }
            return resolvedRevision;
        }
    }
}

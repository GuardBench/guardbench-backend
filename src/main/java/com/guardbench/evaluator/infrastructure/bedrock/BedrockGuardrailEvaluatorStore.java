package com.guardbench.evaluator.infrastructure.bedrock;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** TestRun이 고정한 Bedrock Guardrail Evaluator reference를 조회한다. */
@Repository
class BedrockGuardrailEvaluatorStore {

    private final JdbcTemplate jdbcTemplate;

    BedrockGuardrailEvaluatorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<BedrockGuardrailEvaluator> findByReference(String referenceId) {
        List<BedrockGuardrailEvaluator> rows = jdbcTemplate.query(
                """
                SELECT reference_id, guardrail_identifier, guardrail_revision
                FROM bedrock_guardrail_evaluator
                WHERE reference_id = ?
                """,
                (resultSet, rowNumber) -> new BedrockGuardrailEvaluator(
                        resultSet.getString("reference_id"),
                        resultSet.getString("guardrail_identifier"),
                        resultSet.getString("guardrail_revision")),
                referenceId);
        return rows.stream().findFirst();
    }

    record BedrockGuardrailEvaluator(
            String referenceId,
            String guardrailIdentifier,
            String revision
    ) {
    }
}

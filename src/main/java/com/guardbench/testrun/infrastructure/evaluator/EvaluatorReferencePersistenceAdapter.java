package com.guardbench.testrun.infrastructure.evaluator;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.EvaluatorRegistration;
import com.guardbench.testrun.application.port.out.RegisterEvaluatorReferencePort;
import com.guardbench.testrun.domain.EvaluatorReference;

/** TestRun에 고정된 Evaluator의 provider 설정과 numbered revision을 저장한다. */
@Repository
class EvaluatorReferencePersistenceAdapter implements RegisterEvaluatorReferencePort {
    private final JdbcTemplate jdbcTemplate;

    EvaluatorReferencePersistenceAdapter(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public void register(EvaluatorReference reference, EvaluatorRegistration registration) {
        jdbcTemplate.update("INSERT INTO evaluator_reference(reference_id, evaluator_type) VALUES (?, ?)",
                reference.value(), registration.typeCode());
        jdbcTemplate.update("""
                INSERT INTO bedrock_guardrail_evaluator(reference_id, guardrail_identifier, guardrail_revision)
                VALUES (?, ?, ?)
                """, reference.value(), registration.identifier(), registration.revision());
    }
}

package com.guardbench.testrun.infrastructure.evaluator;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.EvaluatorRegistration;
import com.guardbench.testrun.application.port.out.RegisterEvaluatorReferencePort;
import com.guardbench.testrun.domain.EvaluatorReference;

/** TestRun에 고정된 Response Behavior Classifier의 provider/model 식별자를 저장한다. */
@Repository
class EvaluatorReferencePersistenceAdapter implements RegisterEvaluatorReferencePort {
    private final JdbcTemplate jdbcTemplate;

    EvaluatorReferencePersistenceAdapter(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public void register(EvaluatorReference reference, EvaluatorRegistration registration) {
        jdbcTemplate.update("""
                INSERT INTO evaluator_reference(reference_id, provider_code, model_id) VALUES (?, ?, ?)
                """, reference.value(), registration.providerCode(), registration.modelId());
    }
}

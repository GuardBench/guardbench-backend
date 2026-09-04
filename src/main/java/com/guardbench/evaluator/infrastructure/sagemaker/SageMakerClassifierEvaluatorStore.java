package com.guardbench.evaluator.infrastructure.sagemaker;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** TestRun이 고정한 SageMaker Response Behavior Classifier reference를 조회한다. */
@Repository
class SageMakerClassifierEvaluatorStore {

    private final JdbcTemplate jdbcTemplate;

    SageMakerClassifierEvaluatorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<SageMakerClassifierEvaluator> findByReference(String referenceId) {
        List<SageMakerClassifierEvaluator> rows = jdbcTemplate.query(
                """
                SELECT reference_id, provider_code, model_id
                FROM evaluator_reference
                WHERE reference_id = ?
                """,
                (resultSet, rowNumber) -> new SageMakerClassifierEvaluator(
                        resultSet.getString("reference_id"),
                        resultSet.getString("provider_code"),
                        resultSet.getString("model_id")),
                referenceId);
        return rows.stream().findFirst();
    }

    /**
     * {@code modelId}는 SageMaker 맥락에서 endpoint name을 저장한다. 컬럼 이름은 provider-neutral
     * {@code model_id}를 유지하지만 값의 의미는 provider마다 다르다.
     */
    record SageMakerClassifierEvaluator(
            String referenceId,
            String providerCode,
            String endpointName
    ) {
    }
}

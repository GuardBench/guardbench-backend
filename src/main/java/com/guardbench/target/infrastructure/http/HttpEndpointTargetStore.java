package com.guardbench.target.infrastructure.http;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** HTTP Endpoint Target의 provider 설정을 조회한다. */
@Repository
class HttpEndpointTargetStore {

    private final JdbcTemplate jdbcTemplate;

    HttpEndpointTargetStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<HttpEndpointTarget> findByReference(String referenceId) {
        List<HttpEndpointTarget> rows = jdbcTemplate.query(
                "SELECT reference_id, endpoint_url FROM http_endpoint_target WHERE reference_id = ?",
                (resultSet, rowNumber) -> new HttpEndpointTarget(
                        resultSet.getString("reference_id"),
                        resultSet.getString("endpoint_url")),
                referenceId);
        return rows.stream().findFirst();
    }

    record HttpEndpointTarget(String referenceId, String endpointUrl) {
    }
}

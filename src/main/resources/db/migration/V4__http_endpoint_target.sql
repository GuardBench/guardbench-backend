ALTER TABLE target_reference
    DROP CONSTRAINT ck_target_reference_type,
    ADD CONSTRAINT ck_target_reference_type
        CHECK (target_type IN ('BEDROCK_GUARDRAIL', 'HTTP_ENDPOINT'));

CREATE TABLE http_endpoint_target (
    reference_id TEXT PRIMARY KEY,
    endpoint_url TEXT NOT NULL,

    CONSTRAINT fk_http_endpoint_target_reference
        FOREIGN KEY (reference_id) REFERENCES target_reference(reference_id) ON DELETE RESTRICT,
    CONSTRAINT ck_http_endpoint_url_nonblank CHECK (endpoint_url ~ '[^[:space:]]'),
    CONSTRAINT ck_http_endpoint_url_scheme CHECK (endpoint_url ~* '^https?://')
);

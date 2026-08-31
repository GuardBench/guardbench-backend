ALTER TABLE http_endpoint_target
    DROP CONSTRAINT ck_http_endpoint_url_scheme,
    ADD CONSTRAINT ck_http_endpoint_url_scheme
        CHECK (endpoint_url ~* '^https?://[^/?#[:space:]]+');

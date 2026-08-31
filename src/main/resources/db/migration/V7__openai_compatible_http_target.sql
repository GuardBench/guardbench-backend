ALTER TABLE http_endpoint_target
    ADD COLUMN model TEXT,
    ADD CONSTRAINT ck_http_endpoint_target_model_nonblank
        CHECK (model IS NULL OR model ~ '[^[:space:]]');

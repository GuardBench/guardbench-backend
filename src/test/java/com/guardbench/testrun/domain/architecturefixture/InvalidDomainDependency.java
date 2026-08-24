package com.guardbench.testrun.domain.architecturefixture;

import org.springframework.http.ResponseEntity;

public final class InvalidDomainDependency {

    private ResponseEntity<String> response;

    public ResponseEntity<String> getResponse() {
        return response;
    }
}

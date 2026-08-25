package com.guardbench.testrun.application.port.out;

import java.util.Objects;

public record GuardrailMaterializedVersion(String guardrailIdentifier, String version) {

    public GuardrailMaterializedVersion {
        if (guardrailIdentifier == null || guardrailIdentifier.isBlank()) {
            throw new IllegalArgumentException("guardrail identifier must not be blank");
        }
        if (version == null || !version.matches("[1-9][0-9]{0,7}")) {
            throw new IllegalArgumentException("version must be a positive numeric Guardrail version");
        }
    }
}

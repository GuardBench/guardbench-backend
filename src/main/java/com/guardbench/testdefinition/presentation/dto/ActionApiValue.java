package com.guardbench.testdefinition.presentation.dto;

import com.guardbench.testdefinition.domain.Action;

public enum ActionApiValue {

    ALLOW,
    BLOCK;

    public Action toDomain() {
        return Action.valueOf(name());
    }
}

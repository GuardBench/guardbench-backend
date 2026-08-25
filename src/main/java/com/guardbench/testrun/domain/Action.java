package com.guardbench.testrun.domain;

public enum Action {
    ALLOW,
    BLOCK;

    public static Action fromCode(String code) {
        return Action.valueOf(code);
    }
}

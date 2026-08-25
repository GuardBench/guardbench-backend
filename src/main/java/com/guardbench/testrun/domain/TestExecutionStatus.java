package com.guardbench.testrun.domain;

public enum TestExecutionStatus {
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    NOT_STARTED;

    public boolean isTerminal() {
        return true;
    }
}

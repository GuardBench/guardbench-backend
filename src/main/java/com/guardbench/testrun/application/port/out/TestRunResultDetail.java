package com.guardbench.testrun.application.port.out;

import java.util.Objects;

public record TestRunResultDetail(
        TestRunResultItem item,
        String applicationResponse) {

    public TestRunResultDetail {
        Objects.requireNonNull(item, "result item must not be null");
    }
}

package com.guardbench.testrun.application.port.out;

import java.util.Objects;

public record TestRunResultListView(
        PageResult<TestRunResultItem> page,
        TestRunResultAttentionFacets facets) {

    public TestRunResultListView {
        Objects.requireNonNull(page, "page must not be null");
    }
}

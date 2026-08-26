package com.guardbench.testdefinition.presentation.dto;

import java.util.List;

import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestSuiteSummary;

public record TestSuiteListRes(List<TestSuiteSummaryRes> items, PageMetaRes page) {

    public static TestSuiteListRes from(PageResult<TestSuiteSummary> result) {
        return new TestSuiteListRes(
                result.items().stream().map(TestSuiteSummaryRes::from).toList(),
                PageMetaRes.from(result));
    }
}

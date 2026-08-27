package com.guardbench.testdefinition.presentation.dto;

import java.util.List;

import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestCaseSummary;

public record TestCaseListRes(List<TestCaseListItemRes> items, PageMetaRes page) {

    public static TestCaseListRes from(PageResult<TestCaseSummary> result) {
        return new TestCaseListRes(
                result.items().stream().map(TestCaseListItemRes::from).toList(),
                PageMetaRes.from(result));
    }
}

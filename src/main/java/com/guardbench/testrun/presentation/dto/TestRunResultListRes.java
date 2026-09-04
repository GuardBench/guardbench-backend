package com.guardbench.testrun.presentation.dto;

import java.util.List;

import com.guardbench.common.presentation.dto.PageMetaRes;

/**
 * {@code FINISHED}인 TestRun의 안정된 결과 Collection이다. 필터가 없으면 {@code page.totalElements}는
 * TestRun의 고정 {@code testCaseCount}와 같다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestRunResultListRes</a>
 */
public record TestRunResultListRes(
        List<TestRunResultListItemRes> items,
        PageMetaRes page,
        TestRunResultFacetsRes facets) {
}

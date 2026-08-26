package com.guardbench.testrun.presentation.dto;

import java.util.List;

import com.guardbench.common.presentation.dto.PageMetaRes;

/**
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - TestRunListRes</a>
 */
public record TestRunListRes(List<TestRunListItemRes> items, PageMetaRes page) {
}

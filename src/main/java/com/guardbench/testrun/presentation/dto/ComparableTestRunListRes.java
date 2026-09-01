package com.guardbench.testrun.presentation.dto;

import java.util.List;

import com.guardbench.common.presentation.dto.PageMetaRes;

public record ComparableTestRunListRes(List<ComparableTestRunListItemRes> items, PageMetaRes page) {
}

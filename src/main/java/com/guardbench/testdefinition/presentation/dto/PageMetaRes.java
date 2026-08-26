package com.guardbench.testdefinition.presentation.dto;

import com.guardbench.testdefinition.application.query.PageResult;

public record PageMetaRes(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext) {

    public static PageMetaRes from(PageResult<?> page) {
        return new PageMetaRes(
                page.number(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasPrevious(),
                page.hasNext());
    }
}

package com.guardbench.testrun.application.port.out;

import java.util.List;
import java.util.Objects;

public record PageResult<T>(List<T> items, int number, int size, long totalElements) {
    public PageResult {
        Objects.requireNonNull(items, "items must not be null");
        if (number < 1 || size < 1 || totalElements < 0) {
            throw new IllegalArgumentException("invalid page result");
        }
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> of(List<T> items, PageCriteria page, long totalElements) {
        Objects.requireNonNull(page, "page must not be null");
        return new PageResult<>(items, page.number(), page.size(), totalElements);
    }

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }

    public boolean hasPrevious() {
        return number > 1;
    }

    public boolean hasNext() {
        return number < totalPages();
    }
}

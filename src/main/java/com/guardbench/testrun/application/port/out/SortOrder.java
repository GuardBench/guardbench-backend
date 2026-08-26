package com.guardbench.testrun.application.port.out;

import java.util.Objects;

public record SortOrder<F extends Enum<F>>(F field, SortDirection direction) {
    public SortOrder {
        Objects.requireNonNull(field, "sort field must not be null");
        Objects.requireNonNull(direction, "sort direction must not be null");
    }

    public static <F extends Enum<F>> SortOrder<F> asc(F field) {
        return new SortOrder<>(field, SortDirection.ASC);
    }

    public static <F extends Enum<F>> SortOrder<F> desc(F field) {
        return new SortOrder<>(field, SortDirection.DESC);
    }
}

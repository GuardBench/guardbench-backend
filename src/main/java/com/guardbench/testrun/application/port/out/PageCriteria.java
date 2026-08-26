package com.guardbench.testrun.application.port.out;

public record PageCriteria(int number, int size) {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public PageCriteria {
        if (number < 1) {
            throw new IllegalArgumentException("page number must be at least 1");
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("page size must be between 1 and 100");
        }
    }

    public static PageCriteria firstPage() {
        return new PageCriteria(1, DEFAULT_SIZE);
    }

    public long offset() {
        return (long) (number - 1) * size;
    }
}

package com.guardbench.testdefinition.application.query;

/**
 * Offset Pagination 요청 조건이다.
 *
 * <p>{@code number}는 1부터 시작한다. 승인된 API 계약이 외부 노출 페이지 번호를 1 기반으로 정의하므로
 * 이 타입도 같은 기준을 사용하고, 0 기반 offset 계산은 Infrastructure가 담당한다.
 *
 * <p>Spring의 {@code Pageable}을 사용하지 않는다. 승인된 계약이 Application과 Domain에 Spring 타입을
 * 노출하지 않도록 요구한다.
 *
 * <p>범위 검증은 Presentation이 400으로 먼저 거부한다. 이 타입의 검증은 잘못된 값이 LIMIT과 OFFSET에
 * 도달하지 않게 하는 최후 방어선이다.
 *
 * <p>근거: {@code docs/api/openapi.yaml},
 * {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
public record PageCriteria(int number, int size) {

    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public PageCriteria {
        if (number < 1) {
            throw new IllegalArgumentException("페이지 번호는 1 이상이어야 합니다. number=" + number);
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "페이지 크기는 " + MIN_SIZE + "부터 " + MAX_SIZE + " 사이여야 합니다. size=" + size);
        }
    }

    /**
     * 승인된 기본값으로 첫 페이지를 요청한다.
     */
    public static PageCriteria firstPage() {
        return new PageCriteria(1, DEFAULT_SIZE);
    }

    /**
     * 건너뛸 행 수다. 1 기반 페이지 번호를 0 기반 offset으로 바꾼다.
     */
    public long offset() {
        return (long) (number - 1) * size;
    }
}

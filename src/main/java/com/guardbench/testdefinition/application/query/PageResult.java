package com.guardbench.testdefinition.application.query;

import java.util.List;
import java.util.Objects;

/**
 * Offset Pagination 조회 결과다.
 *
 * <p>{@code totalPages}, {@code hasPrevious}, {@code hasNext}는 저장하지 않고 요청한 페이지와 전체
 * 건수에서 파생한다. 승인된 응답 계약이 이 세 값을 요구하므로, 계산을 이 타입 한 곳에 두어 호출자마다
 * 다시 구현하지 않게 한다.
 *
 * <p>마지막 페이지를 초과한 요청도 오류가 아니다. 승인된 계약이 빈 {@code items}와 요청한 페이지 번호를
 * 그대로 반환하도록 정의하므로 {@code number}를 보정하지 않는다.
 *
 * <p>Spring의 {@code Page}를 사용하지 않는다. 승인된 계약이 Application과 Domain에 Spring 타입을
 * 노출하지 않도록 요구한다.
 *
 * <p>근거: {@code docs/api/openapi.yaml},
 * {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
public record PageResult<T>(List<T> items, int number, int size, long totalElements) {

    public PageResult {
        Objects.requireNonNull(items, "items must not be null");
        if (number < 1) {
            throw new IllegalArgumentException("페이지 번호는 1 이상이어야 합니다. number=" + number);
        }
        if (size < 1) {
            throw new IllegalArgumentException("페이지 크기는 1 이상이어야 합니다. size=" + size);
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException(
                    "전체 건수는 음수일 수 없습니다. totalElements=" + totalElements);
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

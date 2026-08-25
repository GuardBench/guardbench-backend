package com.guardbench.testdefinition.application.query;

import java.util.Objects;

/**
 * 정렬 조건 하나다. 허용된 필드와 방향의 짝으로만 표현한다.
 *
 * <p>정렬 필드를 문자열이 아니라 enum으로 받는다. 승인된 계약이 목록마다 허용 필드를 고정하므로, 허용
 * 목록 밖의 값이 Infrastructure의 query 조립까지 내려가지 못하게 컴파일 시점에 막는다.
 *
 * <p>여러 조건은 요청 순서가 곧 우선순위다. 그 순서는 이 타입이 아니라 목록 조건이 보유한다.
 *
 * <p>근거: {@code docs/api/openapi.yaml}
 */
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

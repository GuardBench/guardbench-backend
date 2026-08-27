package com.guardbench.testdefinition.application.query;

/**
 * 정렬 방향이다.
 *
 * <p>Spring의 {@code Sort.Direction}을 사용하지 않는다. 승인된 계약이 Application과 Domain에 Spring
 * 타입을 노출하지 않도록 요구한다.
 *
 * <p>근거: {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
public enum SortDirection {

    ASC,
    DESC
}

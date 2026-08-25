package com.guardbench.testdefinition.application.query;

/**
 * TestSuite 목록 조회가 허용하는 정렬 필드다.
 *
 * <p>승인된 API 계약의 허용 목록과 1:1로 대응한다. 목록 밖의 필드로 정렬할 수 없다.
 *
 * <p>{@link #TEST_CASE_COUNT}는 저장된 컬럼이 아니라 활성 TestCase를 집계한 값이다. 승인된 계약이 이
 * 값을 TestSuite에 중복 저장하지 않고 조회 시점에 집계하도록 정하므로, 이 필드로 정렬하려면 집계 결과
 * 전체에 정렬을 적용한 뒤 Pagination해야 한다.
 *
 * <p>근거: {@code docs/api/openapi.yaml},
 * {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
public enum TestSuiteSortField {

    NAME,
    CREATED_AT,
    UPDATED_AT,
    TEST_CASE_COUNT,
    ID
}

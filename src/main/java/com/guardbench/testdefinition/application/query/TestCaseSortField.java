package com.guardbench.testdefinition.application.query;

/**
 * TestCase 목록 조회가 허용하는 정렬 필드다.
 *
 * <p>승인된 API 계약의 허용 목록과 1:1로 대응한다. 목록 밖의 필드로 정렬할 수 없다.
 *
 * <p>{@link #SEVERITY}의 오름차순은 {@code LOW, MEDIUM, HIGH, CRITICAL} 순서다. 저장 값이
 * {@code VARCHAR} code이므로 컬럼을 그대로 정렬하면 사전순이 되어 이 계약과 어긋난다. 순서 부여는
 * Infrastructure의 query가 담당한다.
 *
 * <p>근거: {@code docs/api/openapi.yaml}
 */
public enum TestCaseSortField {

    NAME,
    CATEGORY,
    EXPECTED_ACTION,
    SEVERITY,
    CREATED_AT,
    UPDATED_AT,
    ID
}

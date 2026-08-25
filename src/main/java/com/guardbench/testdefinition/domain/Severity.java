package com.guardbench.testdefinition.domain;

/**
 * TestCase 실패가 갖는 영향도다.
 *
 * <p>MVP 평가 계약에서 severity는 조회·필터와 사용자 판단에만 사용하며 Quality Gate Metric에 가중치를
 * 주지 않는다. 따라서 이 enum은 판정 로직을 갖지 않는다.
 *
 * <p>선언 순서는 승인된 API 계약의 오름차순 정렬 기준인 {@code LOW → MEDIUM → HIGH → CRITICAL}과
 * 같게 두었다. 정렬 구현이 이 순서에 의존해야 한다는 뜻은 아니며, 순서가 필요한 조회 기능은 해당 구현
 * 범위에서 정렬 방식을 정한다.
 *
 * <p>근거: {@code docs/domain/evaluation-contract.md}, {@code docs/api/README.md}
 */
public enum Severity {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

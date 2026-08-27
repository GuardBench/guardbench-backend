package com.guardbench.common.presentation.dto;

/**
 * Offset Pagination 메타데이터다. 범위를 초과한 페이지도 {@code number}에는 요청 번호를 유지한다.
 * 필터 결과가 0건이면 {@code totalElements}와 {@code totalPages}는 모두 0이다.
 *
 * @see <a href="../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - PageMetaRes</a>
 */
public record PageMetaRes(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext) {
}

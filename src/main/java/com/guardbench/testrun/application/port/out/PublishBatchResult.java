package com.guardbench.testrun.application.port.out;

import java.util.Set;
import java.util.UUID;

/**
 * SQS {@code SendMessageBatch} 발행의 항목별 결과다.
 *
 * <p>ADR 0005: 한 항목의 발행 실패가 같은 batch의 나머지 항목 처리를 막지 않으므로
 * 성공한 eventId 집합만 반환하고, 요청에 있었지만 이 집합에 없는 eventId는
 * 실패로 간주해 PENDING으로 남긴다.
 */
public record PublishBatchResult(Set<UUID> succeededEventIds) {

    public PublishBatchResult {
        succeededEventIds = Set.copyOf(succeededEventIds);
    }

    public boolean succeeded(UUID eventId) {
        return succeededEventIds.contains(eventId);
    }
}

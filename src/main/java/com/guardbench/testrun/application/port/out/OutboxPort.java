package com.guardbench.testrun.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Outbox 이벤트를 관리하는 consumer-owned 아웃바운드 Port다.
 * Publisher는 SELECT ... FOR UPDATE SKIP LOCKED으로 PENDING batch를 가져온다.
 */
public interface OutboxPort {

    /**
     * PENDING 이벤트를 저장한다. deduplication_key가 중복이면 무시한다(INSERT ... ON CONFLICT DO NOTHING).
     */
    void save(OutboxEventRecord event);

    /**
     * PENDING 상태의 이벤트를 SKIP LOCKED으로 최대 batchSize개 가져온다.
     */
    List<OutboxEventRecord> findPendingBatch(int batchSize);

    /**
     * 지정된 이벤트를 PUBLISHED 상태로 전환한다.
     */
    void markPublished(UUID eventId);
}

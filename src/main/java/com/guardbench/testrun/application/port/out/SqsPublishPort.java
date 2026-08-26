package com.guardbench.testrun.application.port.out;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.guardbench.testrun.application.messaging.TestRunQueue;

/**
 * Outbox Publisher가 event batch를 목적 SQS queue로 발행하는 Port다.
 *
 * <p>ADR 0005: 잠근 PENDING batch를 SQS {@code SendMessageBatch}로 발행하고
 * 항목별 성공 여부를 반환한다. 한 항목의 실패가 같은 batch의 나머지 항목
 * 처리를 막지 않는다. SQS {@code SendMessageBatch}는 queue당 최대 10개
 * 항목을 지원하므로 호출자는 queue별로 묶어 10개 이하로 전달해야 한다.
 */
public interface SqsPublishPort {

    /**
     * event batch를 지정된 queue로 발행한다.
     *
     * @param queue 발행 대상 queue (batch 안 모든 항목이 이 queue로 발행된다)
     * @param entries 발행할 event batch, 항목마다 고유한 eventId를 가져야 한다. 최대 10개.
     * @return eventId별 발행 성공 여부. entries에 있는 모든 eventId가 결과에 포함되어야 한다.
     */
    PublishBatchResult publishBatch(TestRunQueue queue, List<PublishBatchEntry> entries);
    /** 발행할 개별 event다. */
    record PublishBatchEntry(UUID eventId, String payload) {
        public PublishBatchEntry {
            Objects.requireNonNull(eventId, "eventId must not be null");
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("payload must not be blank");
            }
        }
    }
}

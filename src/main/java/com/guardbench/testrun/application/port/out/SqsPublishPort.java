package com.guardbench.testrun.application.port.out;

import com.guardbench.testrun.application.messaging.TestRunQueue;

/** Outbox Publisher가 event payload를 목적 SQS queue로 발행하는 Port다. */
public interface SqsPublishPort {

    /** @return SQS가 수락했으면 true, 재시도해야 하면 false */
    boolean publish(TestRunQueue queue, String payload);
}

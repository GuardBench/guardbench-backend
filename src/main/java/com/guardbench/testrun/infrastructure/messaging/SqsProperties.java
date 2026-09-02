package com.guardbench.testrun.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQS 런타임 설정을 위한 Properties다.
 *
 * <p>ADR 0005에서 승인된 Queue 이름과 운영값을 외부화한다.
 * AWS credential은 DefaultCredentialProvider 체인으로 공급하며 별도 속성은 두지 않는다.
 */
@ConfigurationProperties(prefix = "guardbench.sqs")
public record SqsProperties(
        /** SQS endpoint override (LocalStack, 로컬 개발 등). null이면 SDK 기본값. */
        String endpointOverride,
        /** AWS region (기본 ap-northeast-2). */
        String region,
        /** 각 Queue URL을 직접 지정할 때 사용한다. null이면 Queue 이름으로 조회한다. */
        QueueUrls queueUrls,
        /** Polling 설정 */
        Polling polling,
        /** Outbox 발행 설정 */
        Outbox outbox
) {

    private static final int DEFAULT_VISIBILITY_TIMEOUT_SECONDS = 90;

    public SqsProperties {
        if (region == null || region.isBlank()) {
            region = "ap-northeast-2";
        }
        if (polling == null) {
            polling = new Polling(10, 20, DEFAULT_VISIBILITY_TIMEOUT_SECONDS);
        }
        if (outbox == null) {
            outbox = new Outbox(10);
        }
    }

    public record QueueUrls(
            String resolve,
            String workItems,
            String runFinalize
    ) {}

    /**
     * Outbox 발행 설정이다.
     *
     * <p>발행 트랜잭션이 PENDING row lock을 SQS 전송 동안 유지하므로
     * batch size는 lock 보유 시간을 제한할 수 있도록 작게 유지한다.
     */
    public record Outbox(
            /** 한 번의 발행 트랜잭션에서 처리하는 최대 event 수. */
            int batchSize
    ) {
        public Outbox {
            if (batchSize <= 0) {
                batchSize = 10;
            }
        }
    }

    public record Polling(
            /** 한 번에 가져오는 최대 메시지 수 (1–10). */
            int maxMessages,
            /** long-poll 대기 초 (0–20). */
            int waitTimeSeconds,
            /** visibility timeout 초. */
            int visibilityTimeoutSeconds
    ) {
        public Polling {
            if (maxMessages <= 0 || maxMessages > 10) {
                maxMessages = 10;
            }
            if (waitTimeSeconds < 0 || waitTimeSeconds > 20) {
                waitTimeSeconds = 20;
            }
            if (visibilityTimeoutSeconds <= 0) {
                visibilityTimeoutSeconds = DEFAULT_VISIBILITY_TIMEOUT_SECONDS;
            }
        }
    }
}

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
        Polling polling
) {

    public SqsProperties {
        if (region == null || region.isBlank()) {
            region = "ap-northeast-2";
        }
        if (polling == null) {
            polling = new Polling(10, 20, 30);
        }
    }

    public record QueueUrls(
            String resolve,
            String workItems,
            String runFinalize
    ) {}

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
                visibilityTimeoutSeconds = 30;
            }
        }
    }
}

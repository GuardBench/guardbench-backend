package com.guardbench.testrun.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Worker 전용 실행 설정이다.
 *
 * <p>WorkItems 동시성은 Resolve/Finalize polling과 분리된 bounded worker pool에만 적용한다.
 */
@ConfigurationProperties(prefix = "guardbench.worker")
public record WorkerProperties(
        boolean enabled,
        WorkItems workItems
) {

    public WorkerProperties {
        if (workItems == null) {
            workItems = new WorkItems(1, 30);
        }
    }

    public record WorkItems(
            int concurrency,
            int shutdownTimeoutSeconds
    ) {

        public WorkItems {
            if (concurrency <= 0) {
                throw new IllegalArgumentException("WorkItems concurrency must be positive");
            }
            if (shutdownTimeoutSeconds <= 0) {
                throw new IllegalArgumentException("WorkItems shutdown timeout must be positive");
            }
        }
    }
}

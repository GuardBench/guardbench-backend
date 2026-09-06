package com.guardbench.testdefinition.infrastructure.cleanup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(TestCaseBulkIdempotencyCleanupProperties.class)
@ConditionalOnProperty(
        name = "guardbench.test-case-bulk-idempotency.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
class TestCaseBulkIdempotencyCleanupConfiguration {
}

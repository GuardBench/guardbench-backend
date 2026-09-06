package com.guardbench.testdefinition.infrastructure.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestCaseBulkIdempotencyCleanupPropertiesTest {

    @Test
    @DisplayName("미설정 batch 값은 보수적인 운영 기본값을 사용한다")
    void appliesDefaults() {
        TestCaseBulkIdempotencyCleanupProperties properties =
                new TestCaseBulkIdempotencyCleanupProperties(null, null, null, null);

        assertThat(properties.batchSize()).isEqualTo(500);
        assertThat(properties.maxBatchesPerRun()).isEqualTo(10);
        assertThat(properties.delayMs()).isEqualTo(900_000L);
        assertThat(properties.initialDelayMs()).isEqualTo(60_000L);
    }

    @Test
    @DisplayName("과도한 batch 설정은 애플리케이션 시작 전에 거부한다")
    void rejectsUnsafeBounds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TestCaseBulkIdempotencyCleanupProperties(1001, 10, 900_000L, 60_000L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TestCaseBulkIdempotencyCleanupProperties(500, 101, 900_000L, 60_000L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TestCaseBulkIdempotencyCleanupProperties(500, 10, 999L, 60_000L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TestCaseBulkIdempotencyCleanupProperties(500, 10, 900_000L, -1L));
    }
}

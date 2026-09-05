package com.guardbench.testrun.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkerPropertiesTest {

    @Test
    @DisplayName("WorkItems 설정이 없으면 동시성 1과 종료 timeout 30초를 사용한다")
    void defaultsWorkItemSettings() {
        WorkerProperties properties = new WorkerProperties(false, null);

        assertThat(properties.workItems().concurrency()).isEqualTo(1);
        assertThat(properties.workItems().shutdownTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("WorkItems 동시성은 양의 정수만 허용한다")
    void rejectsNonPositiveConcurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WorkerProperties.WorkItems(0, 30));
    }

    @Test
    @DisplayName("WorkItems 종료 timeout은 양의 정수만 허용한다")
    void rejectsNonPositiveShutdownTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WorkerProperties.WorkItems(1, 0));
    }
}

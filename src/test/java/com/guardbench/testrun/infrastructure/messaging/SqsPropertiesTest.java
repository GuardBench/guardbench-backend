package com.guardbench.testrun.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqsPropertiesTest {

    @Test
    void defaultsPollingVisibilityToNinetySeconds() {
        SqsProperties properties = new SqsProperties(null, null, null, null, null);

        assertThat(properties.polling().visibilityTimeoutSeconds()).isEqualTo(90);
    }

    @Test
    void replacesNonPositivePollingVisibilityWithNinetySeconds() {
        SqsProperties.Polling polling = new SqsProperties.Polling(10, 20, 0);

        assertThat(polling.visibilityTimeoutSeconds()).isEqualTo(90);
    }
}

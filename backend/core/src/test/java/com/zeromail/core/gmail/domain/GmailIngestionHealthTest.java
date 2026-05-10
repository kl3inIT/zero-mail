package com.zeromail.core.gmail.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class GmailIngestionHealthTest {

    @Test
    void allValues_haveStableId() {
        assertThat(GmailIngestionHealth.HEALTHY.id()).isEqualTo("HEALTHY");
        assertThat(GmailIngestionHealth.WATCH_UNHEALTHY.id()).isEqualTo("WATCH_UNHEALTHY");
        assertThat(GmailIngestionHealth.HISTORY_LOST.id()).isEqualTo("HISTORY_LOST");
    }

    @Test
    void fromId_validValues_succeed() {
        assertThat(GmailIngestionHealth.fromId("HEALTHY")).isEqualTo(GmailIngestionHealth.HEALTHY);
        assertThat(GmailIngestionHealth.fromId("WATCH_UNHEALTHY")).isEqualTo(GmailIngestionHealth.WATCH_UNHEALTHY);
        assertThat(GmailIngestionHealth.fromId("HISTORY_LOST")).isEqualTo(GmailIngestionHealth.HISTORY_LOST);
    }

    @Test
    void fromId_unknownId_throwsNoSuchElementException() {
        assertThatThrownBy(() -> GmailIngestionHealth.fromId("BOGUS"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown GmailIngestionHealth");
    }

    @Test
    void idEqualsName() {
        for (GmailIngestionHealth health : GmailIngestionHealth.values()) {
            assertThat(health.id()).isEqualTo(health.name());
        }
    }
}

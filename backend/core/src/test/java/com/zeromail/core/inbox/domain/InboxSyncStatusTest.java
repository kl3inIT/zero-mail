package com.zeromail.core.inbox.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class InboxSyncStatusTest {

    @Test
    void fromId_resolves_each_enum_value_by_its_name() {
        assertThat(InboxSyncStatus.fromId("IDLE")).isEqualTo(InboxSyncStatus.IDLE);
        assertThat(InboxSyncStatus.fromId("BACKFILLING")).isEqualTo(InboxSyncStatus.BACKFILLING);
        assertThat(InboxSyncStatus.fromId("ERROR")).isEqualTo(InboxSyncStatus.ERROR);
    }

    @Test
    void fromId_throws_NoSuchElementException_for_unknown_id() {
        assertThatThrownBy(() -> InboxSyncStatus.fromId("PAUSED"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown InboxSyncStatus id: PAUSED");
    }

    @Test
    void id_equals_name_for_every_value() {
        for (InboxSyncStatus status : InboxSyncStatus.values()) {
            assertThat(status.id()).isEqualTo(status.name());
        }
    }
}

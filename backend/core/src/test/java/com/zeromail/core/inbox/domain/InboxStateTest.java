package com.zeromail.core.inbox.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class InboxStateTest {

    @Test
    void fromId_resolves_each_enum_value_by_its_name() {
        assertThat(InboxState.fromId("INBOX")).isEqualTo(InboxState.INBOX);
        assertThat(InboxState.fromId("OUT_OF_INBOX")).isEqualTo(InboxState.OUT_OF_INBOX);
        assertThat(InboxState.fromId("TOMBSTONED")).isEqualTo(InboxState.TOMBSTONED);
    }

    @Test
    void fromId_throws_NoSuchElementException_for_unknown_id() {
        assertThatThrownBy(() -> InboxState.fromId("ARCHIVED"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown InboxState id: ARCHIVED");
    }

    @Test
    void id_equals_name_for_every_value() {
        for (InboxState state : InboxState.values()) {
            assertThat(state.id()).isEqualTo(state.name());
        }
    }
}

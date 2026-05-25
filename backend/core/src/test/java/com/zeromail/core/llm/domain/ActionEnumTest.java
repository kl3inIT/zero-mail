package com.zeromail.core.llm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ActionEnumTest {

    @Test
    void fromId_returns_correct_enum_for_all_phase_8_1_action_ids() {
        assertThat(Action.fromId("label")).isEqualTo(Action.LABEL);
        assertThat(Action.fromId("archive")).isEqualTo(Action.ARCHIVE);
        assertThat(Action.fromId("save_draft")).isEqualTo(Action.SAVE_DRAFT);
        assertThat(Action.fromId("mark_read")).isEqualTo(Action.MARK_READ);
        assertThat(Action.fromId("star")).isEqualTo(Action.STAR);
        assertThat(Action.fromId("add_to_digest")).isEqualTo(Action.ADD_TO_DIGEST);
        assertThat(Action.fromId("mark_spam")).isEqualTo(Action.MARK_SPAM);
        assertThat(Action.fromId("send_reply")).isEqualTo(Action.SEND_REPLY);
        assertThat(Action.fromId("forward_email")).isEqualTo(Action.FORWARD_EMAIL);
        assertThat(Action.fromId("send_email")).isEqualTo(Action.SEND_EMAIL);
    }

    @Test
    void fromId_throws_on_unknown_id() {
        assertThatThrownBy(() -> Action.fromId("send"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Unknown Action id: send");
    }

    @Test
    void functionName_returns_lower_snake_case() {
        assertThat(Action.LABEL.functionName()).isEqualTo("label");
        assertThat(Action.ARCHIVE.functionName()).isEqualTo("archive");
        assertThat(Action.SAVE_DRAFT.functionName()).isEqualTo("save_draft");
        assertThat(Action.SEND_REPLY.functionName()).isEqualTo("send_reply");
        assertThat(Action.FORWARD_EMAIL.functionName()).isEqualTo("forward_email");
        assertThat(Action.SEND_EMAIL.functionName()).isEqualTo("send_email");
    }
}

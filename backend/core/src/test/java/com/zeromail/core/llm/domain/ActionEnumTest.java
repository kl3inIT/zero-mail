package com.zeromail.core.llm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class ActionEnumTest {

    @Test
    void fromId_returns_correct_enum_for_label_archive_save_draft() {
        assertThat(Action.fromId("label")).isEqualTo(Action.LABEL);
        assertThat(Action.fromId("archive")).isEqualTo(Action.ARCHIVE);
        assertThat(Action.fromId("save_draft")).isEqualTo(Action.SAVE_DRAFT);
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
    }
}

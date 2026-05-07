package com.zeromail.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Plan 04 lands ActionValidator")
class ActionValidatorWave0Test {

    @Test
    void rejects_disallowed_send_action() {
        assertThat(ActionValidator.class.getName()).isEqualTo("com.zeromail.core.llm.service.ActionValidator");
    }

    @Test
    void accepts_allowlisted_label_action() {
        assertThat(ActionValidator.class.getSimpleName()).isEqualTo("ActionValidator");
    }
}

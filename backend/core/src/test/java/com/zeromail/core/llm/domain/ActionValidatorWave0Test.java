package com.zeromail.core.llm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.llm.exception.SafetyViolationException;
import org.junit.jupiter.api.Test;

class ActionValidatorWave0Test {

    @Test
    void rejects_disallowed_send_action() {
        ActionValidator actionValidator = new ActionValidator();

        assertThatThrownBy(() -> actionValidator.validate("send"))
                .isInstanceOf(SafetyViolationException.class);
    }

    @Test
    void accepts_allowlisted_label_action() {
        ActionValidator actionValidator = new ActionValidator();

        assertThat(actionValidator.validate("label")).isEqualTo(Action.LABEL);
    }
}

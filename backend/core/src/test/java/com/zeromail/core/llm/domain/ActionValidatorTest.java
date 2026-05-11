package com.zeromail.core.llm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.llm.exception.SafetyViolationException;
import org.junit.jupiter.api.Test;

class ActionValidatorTest {

    private final ActionValidator actionValidator = new ActionValidator();

    @Test
    void validates_label_archive_save_draft() {
        assertThat(actionValidator.validate("label")).isEqualTo(Action.LABEL);
        assertThat(actionValidator.validate("archive")).isEqualTo(Action.ARCHIVE);
        assertThat(actionValidator.validate("save_draft")).isEqualTo(Action.SAVE_DRAFT);
    }

    @Test
    void rejects_send_action() {
        assertThatThrownBy(() -> actionValidator.validate("send"))
                .isInstanceOf(SafetyViolationException.class);
    }

    @Test
    void rejects_unknown_function_name() {
        assertThatThrownBy(() -> actionValidator.validate("forward"))
                .isInstanceOf(SafetyViolationException.class);
        assertThatThrownBy(() -> actionValidator.validate("delete"))
                .isInstanceOf(SafetyViolationException.class);
        assertThatThrownBy(() -> actionValidator.validate("mark_spam"))
                .isInstanceOf(SafetyViolationException.class);
        assertThatThrownBy(() -> actionValidator.validate("trash"))
                .isInstanceOf(SafetyViolationException.class);
    }

    @Test
    void rejects_null_or_empty() {
        assertThatThrownBy(() -> actionValidator.validate(null))
                .isInstanceOf(SafetyViolationException.class);
        assertThatThrownBy(() -> actionValidator.validate(""))
                .isInstanceOf(SafetyViolationException.class);
        assertThatThrownBy(() -> actionValidator.validate("   "))
                .isInstanceOf(SafetyViolationException.class);
    }

    @Test
    void exception_carries_no_action_name() {
        assertThatThrownBy(() -> actionValidator.validate("send"))
                .isInstanceOf(SafetyViolationException.class)
                .hasMessage(null);
    }

    @Test
    void safety_violation_exception_has_only_no_arg_constructor() {
        assertThat(SafetyViolationException.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(
                        constructor -> {
                            assertThat(constructor.getParameterCount()).isZero();
                            assertThat(constructor.getParameterTypes()).isEmpty();
                        });
    }
}

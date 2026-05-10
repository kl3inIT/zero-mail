package com.zeromail.core.llm.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class SafetyViolationExceptionTest {

    @Test
    void no_message_constructor_only() {
        SafetyViolationException safetyViolationException = new SafetyViolationException();

        assertThat(safetyViolationException.getMessage()).isNull();
        assertThat(SafetyViolationException.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> {
                    assertThat(constructor.getParameterCount()).isZero();
                    assertThat(Arrays.asList(constructor.getParameterTypes())).isEmpty();
                });
    }
}

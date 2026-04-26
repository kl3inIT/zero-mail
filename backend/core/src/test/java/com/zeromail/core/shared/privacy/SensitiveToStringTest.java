package com.zeromail.core.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SensitiveToStringTest {

    @Test
    void toString_is_redacted() {
        Sensitive<String> s = Sensitive.of("super-secret");
        assertThat(s.toString()).isEqualTo("***REDACTED***");
        assertThat("x=" + s).isEqualTo("x=***REDACTED***");
        assertThat(String.valueOf(s)).isEqualTo("***REDACTED***");
    }

    @Test
    void jackson_serializes_redacted() throws Exception {
        var om = new ObjectMapper().registerModule(new SensitiveJacksonModule());
        assertThat(om.writeValueAsString(Sensitive.of("super-secret"))).isEqualTo("\"***REDACTED***\"");
    }

    @Test
    void null_value_rejected() {
        assertThatThrownBy(() -> new Sensitive<>(null)).isInstanceOf(IllegalArgumentException.class);
    }
}

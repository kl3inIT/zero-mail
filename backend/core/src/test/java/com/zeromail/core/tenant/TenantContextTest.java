package com.zeromail.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantContextTest {

    @Test
    void unbound_throws() {
        assertThatThrownBy(TenantContext::currentOrThrow).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bound_returns_value() {
        ScopedValue.where(TenantContext.TENANT, "t-1")
                .run(() -> assertThat(TenantContext.currentOrThrow()).isEqualTo("t-1"));
    }
}

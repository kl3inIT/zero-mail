package com.zeromail.core.tenant;

import java.util.Optional;

public final class TenantContext {

    public static final ScopedValue<String> TENANT = ScopedValue.newInstance();

    private TenantContext() {}

    public static String currentOrThrow() {
        if (!TENANT.isBound()) {
            throw new IllegalStateException("No tenant bound on this thread");
        }
        return TENANT.get();
    }

    public static Optional<String> currentOptional() {
        return TENANT.isBound() ? Optional.of(TENANT.get()) : Optional.empty();
    }
}

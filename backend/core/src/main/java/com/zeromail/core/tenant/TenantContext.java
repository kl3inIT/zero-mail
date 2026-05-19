package com.zeromail.core.tenant;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {

    public static final ScopedValue<String> TENANT = ScopedValue.newInstance();

    private TenantContext() {}

    public static String currentOrThrow() {
        if (!TENANT.isBound()) {
            throw new IllegalStateException("No TenantContext tenant bound on this thread");
        }
        return TENANT.get();
    }

    public static void requireUnbound() {
        if (TENANT.isBound()) {
            throw new IllegalStateException("TenantContext cannot be bound inside AdminContext");
        }
    }

    /** Convenience for callers that immediately reify the bound tenant as a {@link UUID}. */
    public static UUID currentTenantUuid() {
        return UUID.fromString(currentOrThrow());
    }

    public static Optional<String> currentOptional() {
        return TENANT.isBound() ? Optional.of(TENANT.get()) : Optional.empty();
    }

    /**
     * Canonical tenant rebind helper for asynchronous consumers such as triage orchestrators and
     * worker schedulers.
     */
    public static void runWith(UUID tenantId, Runnable action) {
        ScopedValue.where(TENANT, tenantId.toString()).run(action);
    }
}

package com.zeromail.core.admin.auth;

import java.util.Optional;
import java.util.function.Supplier;

public final class AdminContext {

    public static final ScopedValue<AdminUser> ADMIN = ScopedValue.newInstance();

    private AdminContext() {}

    public static AdminUser currentOrThrow() {
        requireTenantContextUnbound();
        if (!ADMIN.isBound()) {
            throw new IllegalStateException("No AdminContext admin bound on this thread");
        }
        return ADMIN.get();
    }

    public static Optional<AdminUser> currentOptional() {
        return ADMIN.isBound() ? Optional.of(ADMIN.get()) : Optional.empty();
    }

    public static void run(AdminUser adminUser, Runnable action) {
        requireTenantContextUnbound();
        ScopedValue.where(ADMIN, adminUser).run(action);
    }

    public static <T> T run(AdminUser adminUser, Supplier<T> action) {
        requireTenantContextUnbound();
        ResultBox<T> resultBox = new ResultBox<>();
        ScopedValue.where(ADMIN, adminUser).run(() -> resultBox.value = action.get());
        return resultBox.value;
    }

    private static void requireTenantContextUnbound() {
        if (tenantContextBound()) {
            throw new IllegalStateException("TenantContext cannot be bound inside AdminContext");
        }
    }

    private static boolean tenantContextBound() {
        try {
            Class<?> tenantContextClass = Class.forName("com.zeromail.core.tenant.TenantContext");
            ScopedValue<?> tenantScopedValue =
                    (ScopedValue<?>) tenantContextClass.getField("TENANT").get(null);
            return tenantScopedValue.isBound();
        } catch (ReflectiveOperationException reflectionException) {
            throw new IllegalStateException(
                    "Unable to inspect TenantContext binding", reflectionException);
        }
    }

    private static final class ResultBox<T> {
        private T value;
    }
}

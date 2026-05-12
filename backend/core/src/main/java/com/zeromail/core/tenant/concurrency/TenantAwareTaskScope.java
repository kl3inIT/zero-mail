package com.zeromail.core.tenant.concurrency;

import com.zeromail.core.tenant.TenantContext;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

public final class TenantAwareTaskScope implements AutoCloseable {

    private final StructuredTaskScope<Object, Void> structuredTaskScope;
    private final String tenantId;

    private TenantAwareTaskScope(
            String tenantId, StructuredTaskScope<Object, Void> structuredTaskScope) {
        this.tenantId = tenantId;
        this.structuredTaskScope = structuredTaskScope;
    }

    public static TenantAwareTaskScope openInherit() {
        String currentTenantId = TenantContext.currentOrThrow();
        return new TenantAwareTaskScope(currentTenantId, StructuredTaskScope.open());
    }

    public <T> StructuredTaskScope.Subtask<T> fork(Callable<T> task) {
        return structuredTaskScope.fork(
                () -> ScopedValue.where(TenantContext.TENANT, tenantId).call(task::call));
    }

    public void join() throws InterruptedException {
        structuredTaskScope.join();
    }

    @Override
    public void close() {
        structuredTaskScope.close();
    }
}

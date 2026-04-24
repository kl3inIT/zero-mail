package com.zeromail.core.tenant.concurrency;

import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

import com.zeromail.core.tenant.TenantContext;

public final class TenantAwareTaskScope implements AutoCloseable {

    private final StructuredTaskScope<Object, Void> inner;
    private final String tenant;

    private TenantAwareTaskScope(String tenant, StructuredTaskScope<Object, Void> inner) {
        this.tenant = tenant;
        this.inner = inner;
    }

    public static TenantAwareTaskScope openInherit() {
        String t = TenantContext.currentOrThrow();
        return new TenantAwareTaskScope(t, StructuredTaskScope.open());
    }

    public <T> StructuredTaskScope.Subtask<T> fork(Callable<T> task) {
        return inner.fork(() -> ScopedValue.where(TenantContext.TENANT, tenant).call(task::call));
    }

    public void join() throws InterruptedException {
        inner.join();
    }

    @Override
    public void close() {
        inner.close();
    }
}

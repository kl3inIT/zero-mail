package com.zeromail.core.chat.llm;

import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Component
public class TenantAwareReactorScheduler {

    /**
     * Returns a fresh scheduler for the currently-bound tenant. Callers own disposal after the
     * stream terminates.
     */
    public Scheduler scheduler() {
        String capturedTenantId = TenantContext.currentOrThrow();
        ExecutorService delegateExecutor = Executors.newVirtualThreadPerTaskExecutor();
        return Schedulers.fromExecutorService(
                new TenantBindingExecutorService(delegateExecutor, capturedTenantId));
    }

    private static final class TenantBindingExecutorService extends AbstractExecutorService {

        private final ExecutorService delegateExecutor;
        private final String tenantId;

        private TenantBindingExecutorService(ExecutorService delegateExecutor, String tenantId) {
            this.delegateExecutor = delegateExecutor;
            this.tenantId = tenantId;
        }

        @Override
        public void shutdown() {
            delegateExecutor.shutdown();
        }

        @Override
        public @NonNull List<Runnable> shutdownNow() {
            return delegateExecutor.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegateExecutor.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegateExecutor.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, @NonNull TimeUnit unit)
                throws InterruptedException {
            return delegateExecutor.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(@NonNull Runnable command) {
            delegateExecutor.execute(
                    () -> ScopedValue.where(TenantContext.TENANT, tenantId).run(command));
        }
    }
}

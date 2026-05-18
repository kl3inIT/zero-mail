package com.zeromail.core.chat.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.tenant.TenantContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

class MultiTenantChatLeakIT {

    @Test
    void tenant_context_is_rebound_for_fifty_concurrent_stream_tasks() {
        TenantAwareReactorScheduler schedulerFactory = new TenantAwareReactorScheduler();
        ExecutorService launchExecutor = Executors.newVirtualThreadPerTaskExecutor();
        List<TenantStreamResult> streamResults = new ArrayList<>();
        List<CompletableFuture<TenantStreamResult>> streamFutures = new ArrayList<>();

        for (int tenantIndex = 0; tenantIndex < 10; tenantIndex++) {
            String tenantId = UUID.randomUUID().toString();
            for (int streamIndex = 0; streamIndex < 5; streamIndex++) {
                streamFutures.add(
                        CompletableFuture.supplyAsync(
                                () -> runStreamTask(schedulerFactory, tenantId), launchExecutor));
            }
        }

        CompletableFuture.allOf(streamFutures.toArray(CompletableFuture[]::new)).join();
        streamFutures.forEach(streamFuture -> streamResults.add(streamFuture.join()));
        launchExecutor.shutdownNow();

        assertThat(streamResults).hasSize(50);
        assertThat(streamResults)
                .allSatisfy(
                        streamResult ->
                                assertThat(streamResult.observedTenantId())
                                        .isEqualTo(streamResult.expectedTenantId()));
    }

    private static TenantStreamResult runStreamTask(
            TenantAwareReactorScheduler schedulerFactory, String tenantId) {
        try {
            return ScopedValue.where(TenantContext.TENANT, tenantId)
                    .call(
                            () -> {
                                Scheduler scheduler = schedulerFactory.scheduler();
                                try {
                                    String observedTenantId =
                                            Mono.fromCallable(TenantContext::currentOrThrow)
                                                    .subscribeOn(scheduler)
                                                    .block(Duration.ofSeconds(5));
                                    return new TenantStreamResult(tenantId, observedTenantId);
                                } finally {
                                    scheduler.dispose();
                                }
                            });
        } catch (Exception taskFailure) {
            throw new IllegalStateException("tenant stream task failed", taskFailure);
        }
    }

    private record TenantStreamResult(String expectedTenantId, String observedTenantId) {}
}

package com.zeromail.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.zeromail.core.tenant.concurrency.TenantAwareTaskScope;

class TenantAwareTaskScopeTest {

    @Test
    void fanout_preserves_tenant() {
        ScopedValue.where(TenantContext.TENANT, "tenant-A").run(() -> {
            try (var scope = TenantAwareTaskScope.openInherit()) {
                List<StructuredTaskScope.Subtask<String>> subs = IntStream.range(0, 10)
                        .mapToObj(i -> scope.<String>fork(TenantContext::currentOrThrow))
                        .toList();
                scope.join();
                subs.forEach(s -> assertThat(s.get()).isEqualTo("tenant-A"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }
}

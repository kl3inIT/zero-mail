package com.zeromail.core.chat.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class AssistantMemoryRepositoryIT extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired AssistantMemoryJpaRepository assistantMemoryRepository;

    @Test
    void saves_and_searches_tenant_scoped_memory() {
        UUID tenantId = seedTenant();

        withTenant(
                tenantId,
                () ->
                        assistantMemoryRepository.saveAndFlush(
                                new AssistantMemoryEntity(
                                        UUID.randomUUID(),
                                        tenantId,
                                        "Acme là khách hàng chính",
                                        "chat")));

        assertThat(
                        withTenant(
                                tenantId,
                                () ->
                                        assistantMemoryRepository
                                                .findByTenantIdAndContentContainingIgnoreCase(
                                                        tenantId, "Acme", PageRequest.of(0, 10))))
                .extracting(AssistantMemoryEntity::getContent)
                .containsExactly("Acme là khách hàng chính");
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "assistant-memory");
        return tenantId;
    }

    private static <T> T withTenant(UUID tenantId, TenantOperation<T> tenantOperation) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantOperation::run);
    }

    @FunctionalInterface
    private interface TenantOperation<T> {
        T run();
    }
}

package com.zeromail.core.admin.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.tenant.usecases.TenantDeletionRegistry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TenantDeletionCoverageTest {

    @Test
    void deletion_registry_covers_known_tenant_owned_tables() {
        Set<String> tableNames =
                new TenantDeletionRegistry()
                        .orderedDeletionPath().stream()
                                .map(TenantDeletionRegistry.TenantOwnedTable::tableName)
                                .collect(Collectors.toSet());

        assertThat(tableNames)
                .contains(
                        "tenants",
                        "users",
                        "gmail_connections",
                        "rules",
                        "triage_audit",
                        "chat",
                        "chat_message",
                        "tenant_byok_credentials",
                        "assistant_settings",
                        "pubsub_delivery");
    }
}

package com.zeromail.core.admin.tenant.usecases;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TenantDeletionRegistry {

    private final List<TenantOwnedTable> orderedDeletionPath;

    public TenantDeletionRegistry() {
        orderedDeletionPath =
                List.of(
                                new TenantOwnedTable("assistant_pending_action", "tenant_id", 10),
                                new TenantOwnedTable("assistant_action_audit", "tenant_id", 20),
                                new TenantOwnedTable("chat_message", "tenant_id", 30),
                                new TenantOwnedTable("chat", "tenant_id", 40),
                                new TenantOwnedTable(
                                        "assistant_knowledge_snippet", "tenant_id", 50),
                                new TenantOwnedTable("assistant_settings", "tenant_id", 70),
                                new TenantOwnedTable("tenant_byok_credentials", "tenant_id", 80),
                                new TenantOwnedTable("credit_reservation", "tenant_id", 90),
                                new TenantOwnedTable("credit_ledger_entry", "tenant_id", 100),
                                new TenantOwnedTable("credit_grant", "tenant_id", 110),
                                new TenantOwnedTable("rules", "tenant_id", 130),
                                new TenantOwnedTable("triage_audit", "tenant_id", 140),
                                new TenantOwnedTable("mail_message_observed", "tenant_id", 150),
                                new TenantOwnedTable("pubsub_delivery", "tenant_id", 160),
                                new TenantOwnedTable("digest_delivery", "tenant_id", 170),
                                new TenantOwnedTable("notification_preference", "tenant_id", 180),
                                new TenantOwnedTable("thread_reply_status", "tenant_id", 190),
                                new TenantOwnedTable("tenant_sender_opt_in", "tenant_id", 200),
                                new TenantOwnedTable(
                                        "tenant_protected_sender_observation", "tenant_id", 210),
                                new TenantOwnedTable("onboarding_selections", "tenant_id", 220),
                                new TenantOwnedTable("users", "tenant_id", 230),
                                new TenantOwnedTable("gmail_connections", "tenant_id", 240),
                                new TenantOwnedTable("tenants", "id", 250))
                        .stream()
                        .sorted(Comparator.comparingInt(TenantOwnedTable::deletionOrder))
                        .toList();
    }

    public List<TenantOwnedTable> orderedDeletionPath() {
        return orderedDeletionPath;
    }

    public record TenantOwnedTable(String tableName, String tenantIdColumn, int deletionOrder) {

        public TenantOwnedTable {
            Objects.requireNonNull(tableName, "tableName must not be null");
            Objects.requireNonNull(tenantIdColumn, "tenantIdColumn must not be null");
        }
    }
}

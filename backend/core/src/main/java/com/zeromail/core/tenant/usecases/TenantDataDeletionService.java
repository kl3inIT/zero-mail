package com.zeromail.core.tenant.usecases;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges all tenant-owned runtime data for account deletion.
 *
 * <p>The table list is intentionally explicit. Account deletion must not depend on every future
 * table remembering to add a database cascade, and table names never come from request input.
 */
@Service
public class TenantDataDeletionService {

    private final JdbcTemplate jdbcTemplate;

    public TenantDataDeletionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Transactional
    public void deleteTenantData(UUID tenantId) {
        for (TenantRetainedTable tenantRetainedTable : RETAINED_ANONYMIZED_TABLES) {
            jdbcTemplate.update(tenantRetainedTable.anonymizeSql(), tenantId);
        }
        for (TenantOwnedTable tenantOwnedTable : OPTIONAL_DELETION_PATH) {
            if (tableExists(tenantOwnedTable.tableName())) {
                jdbcTemplate.update(tenantOwnedTable.deleteSql(), tenantId);
            }
        }
        for (TenantOwnedTable tenantOwnedTable : DELETION_PATH) {
            jdbcTemplate.update(tenantOwnedTable.deleteSql(), tenantId);
        }
    }

    private static final List<TenantOwnedTable> DELETION_PATH =
            List.of(
                    new TenantOwnedTable("assistant_pending_action", "tenant_id"),
                    new TenantOwnedTable("assistant_action_audit", "tenant_id"),
                    new TenantOwnedTable("chat_message", "tenant_id"),
                    new TenantOwnedTable("chat", "tenant_id"),
                    new TenantOwnedTable("assistant_knowledge_snippet", "tenant_id"),
                    new TenantOwnedTable("assistant_settings", "tenant_id"),
                    new TenantOwnedTable("user_byok_key", "tenant_id"),
                    new TenantOwnedTable("tenant_byok_credentials", "tenant_id"),
                    new TenantOwnedTable("credit_reservation", "tenant_id"),
                    new TenantOwnedTable("credit_ledger_entry", "tenant_id"),
                    new TenantOwnedTable("credit_grant", "tenant_id"),
                    new TenantOwnedTable("billing_bank_transfer_intent", "tenant_id"),
                    new TenantOwnedTable("billing_checkout_session", "tenant_id"),
                    new TenantOwnedTable("billing_plan_period", "tenant_id"),
                    new TenantOwnedTable("billing_webhook_event", "tenant_id"),
                    new TenantOwnedTable("unsubscribe_campaign", "tenant_id"),
                    new TenantOwnedTable("cleanup_sender_projection", "tenant_id"),
                    new TenantOwnedTable("cleanup_sender_status", "tenant_id"),
                    new TenantOwnedTable("sender_suppression", "tenant_id"),
                    new TenantOwnedTable("rules", "tenant_id"),
                    new TenantOwnedTable("rule_automation_settings", "tenant_id"),
                    new TenantOwnedTable("triage_audit", "tenant_id"),
                    new TenantOwnedTable("gmail_inbox_projection", "tenant_id"),
                    new TenantOwnedTable("gmail_inbox_sync_state", "tenant_id"),
                    new TenantOwnedTable("mail_message_observed", "tenant_id"),
                    new TenantOwnedTable("pubsub_delivery", "tenant_id"),
                    new TenantOwnedTable("processing_job", "tenant_id"),
                    new TenantOwnedTable("digest_delivery", "tenant_id"),
                    new TenantOwnedTable("notification_preference", "tenant_id"),
                    new TenantOwnedTable("thread_reply_status", "tenant_id"),
                    new TenantOwnedTable("tenant_sender_opt_in", "tenant_id"),
                    new TenantOwnedTable("tenant_protected_sender_observation", "tenant_id"),
                    new TenantOwnedTable("onboarding_selections", "tenant_id"),
                    new TenantOwnedTable("gmail_connections", "tenant_id"),
                    new TenantOwnedTable("users", "tenant_id"),
                    new TenantOwnedTable("tenants", "id"));

    private static final List<TenantOwnedTable> OPTIONAL_DELETION_PATH =
            List.of(
                    new TenantOwnedTable("outbound_action_audit", "tenant_id"),
                    new TenantOwnedTable("telegram_notification_log", "tenant_id"),
                    new TenantOwnedTable("telegram_notification_dedup", "tenant_id"),
                    new TenantOwnedTable("telegram_account", "tenant_id"));

    private static final List<TenantRetainedTable> RETAINED_ANONYMIZED_TABLES =
            List.of(new TenantRetainedTable("llm_call_audit", "tenant_id"));

    private boolean tableExists(String tableName) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = current_schema()
                              AND table_name = ?
                        )
                        """,
                        Boolean.class,
                        tableName);
        return Boolean.TRUE.equals(exists);
    }

    private record TenantOwnedTable(String tableName, String tenantIdColumn) {

        private TenantOwnedTable {
            Objects.requireNonNull(tableName, "tableName must not be null");
            Objects.requireNonNull(tenantIdColumn, "tenantIdColumn must not be null");
        }

        String deleteSql() {
            return "DELETE FROM " + tableName + " WHERE " + tenantIdColumn + " = ?";
        }
    }

    private record TenantRetainedTable(String tableName, String tenantIdColumn) {

        private TenantRetainedTable {
            Objects.requireNonNull(tableName, "tableName must not be null");
            Objects.requireNonNull(tenantIdColumn, "tenantIdColumn must not be null");
        }

        String anonymizeSql() {
            return "UPDATE "
                    + tableName
                    + " SET "
                    + tenantIdColumn
                    + " = NULL WHERE "
                    + tenantIdColumn
                    + " = ?";
        }
    }
}

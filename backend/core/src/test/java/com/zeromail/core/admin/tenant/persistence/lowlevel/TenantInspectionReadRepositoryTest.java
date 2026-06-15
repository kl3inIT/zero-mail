package com.zeromail.core.admin.tenant.persistence.lowlevel;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.tenant.projection.TenantActivityEvent;
import com.zeromail.core.admin.tenant.projection.TenantListPage;
import com.zeromail.core.admin.tenant.projection.TenantListQuery;
import com.zeromail.core.admin.tenant.projection.TenantListRow;
import com.zeromail.core.admin.tenant.projection.TenantListSummary;
import com.zeromail.core.admin.tenant.usecases.TenantInspectionService;
import com.zeromail.core.support.PostgresContainerTest;
import java.lang.reflect.RecordComponent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantInspectionReadRepositoryTest extends PostgresContainerTest {

    private static final Instant FILTER_FROM = Instant.parse("2042-01-01T00:00:00Z");
    private static final Instant FILTER_TO = Instant.parse("2042-01-02T00:00:00Z");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TenantInspectionService tenantInspectionService;

    @BeforeEach
    void cleanSeedWindow() {
        jdbcTemplate.update(
                "DELETE FROM tenants WHERE created_at >= ? AND created_at <= ?",
                Timestamp.from(FILTER_FROM),
                Timestamp.from(FILTER_TO));
    }

    @Test
    void tenant_list_rows_include_operational_activity_metrics_and_summary() {
        UUID activeTenantId = UUID.fromString("00000000-0000-4000-8000-000000009101");
        UUID disconnectedTenantId = UUID.fromString("00000000-0000-4000-8000-000000009102");
        seedActiveTenantWithOperations(activeTenantId);
        seedDisconnectedTenant(disconnectedTenantId);

        TenantListPage tenantListPage =
                tenantInspectionService.listTenants(
                        new TenantListQuery(25, 0, null, FILTER_FROM, FILTER_TO, null));

        assertThat(tenantListPage.rows()).hasSize(2);
        TenantListRow activeTenantRow =
                tenantListPage.rows().stream()
                        .filter(tenantListRow -> tenantListRow.tenantId().equals(activeTenantId))
                        .findFirst()
                        .orElseThrow();
        assertThat(activeTenantRow.lastActivityKind()).isEqualTo("ASSISTANT_ACTION");
        assertThat(activeTenantRow.gmailConnectionStatus()).isEqualTo("CONNECTED");
        assertThat(activeTenantRow.totalRulesCount()).isEqualTo(2);
        assertThat(activeTenantRow.enabledRulesCount()).isEqualTo(1);
        assertThat(activeTenantRow.enabledRuleNames()).containsExactly("Receipts");
        assertThat(activeTenantRow.observedEmail30dCount()).isEqualTo(2);
        assertThat(activeTenantRow.triageAction30dCount()).isEqualTo(3);
        assertThat(activeTenantRow.failedTriageAction30dCount()).isEqualTo(1);
        assertThat(activeTenantRow.outboundAction30dCount()).isEqualTo(2);
        assertThat(activeTenantRow.blockedOutboundAction30dCount()).isEqualTo(1);
        assertThat(activeTenantRow.chatSessionCount()).isEqualTo(1);
        assertThat(activeTenantRow.lastChatSessionAt())
                .isEqualTo(Instant.parse("2042-01-01T10:10:00Z"));
        assertThat(activeTenantRow.assistantAction30dCount()).isEqualTo(1);
        assertThat(activeTenantRow.llmCall30dCount()).isEqualTo(2);
        assertThat(activeTenantRow.creditBalance()).isEqualTo(95);
        assertThat(activeTenantRow.pubsubBacklogCount()).isEqualTo(2);
        assertThat(activeTenantRow.gmailWatchStatus()).isEqualTo("WATCHING");
        assertThat(activeTenantRow.telegramStatus()).isEqualTo("CONNECTED");
        assertThat(activeTenantRow.telegramLastActiveAt())
                .isEqualTo(Instant.parse("2042-01-01T10:12:30Z"));
        assertThat(activeTenantRow.autoSendRulesEnabled()).isFalse();

        TenantListSummary summary = tenantListPage.summary();
        assertThat(summary.totalCount()).isEqualTo(2);
        assertThat(summary.activeCount()).isEqualTo(1);
        assertThat(summary.disconnectedCount()).isEqualTo(1);
        assertThat(summary.gmailConnectedCount()).isEqualTo(1);
        assertThat(summary.telegramConnectedCount()).isEqualTo(1);
        assertThat(summary.gmailUnhealthyCount()).isEqualTo(2);
        assertThat(summary.automationFailure30dCount()).isEqualTo(2);
        assertThat(summary.outboundBlocked30dCount()).isEqualTo(1);
        assertThat(summary.lowCreditCount()).isEqualTo(1);

        TenantListPage emailFilteredTenantListPage =
                tenantInspectionService.listTenants(
                        new TenantListQuery(
                                25, 0, null, FILTER_FROM, FILTER_TO, "active-ops@example.com"));
        assertThat(emailFilteredTenantListPage.rows())
                .extracting(TenantListRow::tenantId)
                .containsExactly(activeTenantId);
        assertThat(emailFilteredTenantListPage.summary().totalCount()).isEqualTo(1);

        var activeTenantOverview = tenantInspectionService.getOverview(activeTenantId);
        assertThat(activeTenantOverview.gmailConnectionStatus()).isEqualTo("CONNECTED");
        assertThat(activeTenantOverview.telegramStatus()).isEqualTo("CONNECTED");
        assertThat(activeTenantOverview.enabledRulesCount()).isEqualTo(1);
        assertThat(activeTenantOverview.enabledRuleNames()).containsExactly("Receipts");
    }

    @Test
    void tenant_activity_includes_new_login_events_and_legacy_operational_events() {
        UUID tenantId = UUID.fromString("00000000-0000-4000-8000-000000009103");
        seedActiveTenantWithOperations(tenantId);
        jdbcTemplate.update(
                """
                        INSERT INTO tenant_activity_event(
                            id, tenant_id, event_type, event_status, detail,
                            occurred_at, duration_seconds, source, metadata_json, created_at
                        )
                        VALUES (?, ?, 'LOGIN', 'SUCCESS', 'Đăng nhập thành công qua web',
                                ?, 420, 'AUTH', '{}'::jsonb, ?)
                        """,
                UUID.randomUUID(),
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:19:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:19:00Z")));

        var tenantActivitySnapshot = tenantInspectionService.getActivity(tenantId);

        assertThat(tenantActivitySnapshot.totalActivity7dCount()).isGreaterThanOrEqualTo(8);
        assertThat(tenantActivitySnapshot.lastLoginAt())
                .isEqualTo(Instant.parse("2042-01-01T10:19:00Z"));
        assertThat(tenantActivitySnapshot.totalAppDurationSeconds()).isEqualTo(420);
        assertThat(tenantActivitySnapshot.events())
                .extracting("eventType")
                .contains(
                        "LOGIN",
                        "GMAIL_CONNECTED",
                        "RULE_UPDATED",
                        "TRIAGE_ACTION",
                        "CHAT_SESSION");
        assertThat(tenantActivitySnapshot.events().getFirst().eventType()).isEqualTo("LOGIN");
        assertThat(tenantActivitySnapshot.events().getFirst().legacyDataMissing()).isFalse();
        assertThat(tenantActivitySnapshot.events())
                .anySatisfy(
                        tenantActivityEvent -> {
                            assertThat(tenantActivityEvent.eventType()).isEqualTo("RULE_UPDATED");
                            assertThat(tenantActivityEvent.legacyDataMissing()).isTrue();
                        });
        assertThat(
                        Arrays.stream(TenantActivityEvent.class.getRecordComponents())
                                .map(RecordComponent::getName))
                .doesNotContain("deviceFamily", "userAgent");
    }

    @Test
    void tenant_billing_uses_current_plan_period_and_defaults_to_free() {
        UUID paidTenantId = UUID.fromString("00000000-0000-4000-8000-000000009104");
        UUID freeTenantId = UUID.fromString("00000000-0000-4000-8000-000000009105");
        seedActiveTenantWithOperations(paidTenantId);
        seedDisconnectedTenant(freeTenantId);
        attachCurrentPlanPeriod(paidTenantId, "PLUS");

        var paidTenantBilling = tenantInspectionService.getBilling(paidTenantId);
        var freeTenantBilling = tenantInspectionService.getBilling(freeTenantId);

        assertThat(paidTenantBilling.plan()).isEqualTo("PLUS");
        assertThat(freeTenantBilling.plan()).isEqualTo("FREE");
    }

    private void seedActiveTenantWithOperations(UUID tenantId) {
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name, created_at) VALUES (?, ?, ?)",
                tenantId,
                "active-operations-tenant",
                Timestamp.from(Instant.parse("2042-01-01T10:00:00Z")));
        jdbcTemplate.update(
                """
                        INSERT INTO gmail_connections(
                            id, tenant_id, google_email, status, refresh_token_encrypted, scopes_granted,
                            connected_at, created_at, updated_at, watch_expires_at
                        )
                        VALUES (?, ?, ?, 'CONNECTED', NULL, 'gmail.modify',
                                ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                tenantId,
                "active-ops@example.com",
                Timestamp.from(Instant.parse("2042-01-01T10:01:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:01:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:02:00Z")),
                Timestamp.from(Instant.parse("2042-01-03T00:00:00Z")));
        jdbcTemplate.update(
                """
                        INSERT INTO rules(
                            id, tenant_id, display_name, source_text, source_language, schema_version,
                            matcher_ast, action_intents, enabled, order_index, created_at, updated_at
                        )
                        VALUES
                            (?, ?, 'Receipts', 'Archive receipts', 'en', 'rules.v1',
                             '{}'::jsonb, '[]'::jsonb, true, 0, ?, ?),
                            (?, ?, 'Newsletters', 'Label newsletters', 'en', 'rules.v1',
                             '{}'::jsonb, '[]'::jsonb, false, 1, ?, ?)
                        """,
                UUID.randomUUID(),
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:03:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:03:00Z")),
                UUID.randomUUID(),
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:04:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:04:00Z")));
        jdbcTemplate.update(
                """
                        INSERT INTO mail_message_observed(
                            tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids,
                            observed_at
                        )
                        VALUES
                            (?, 'msg-1', 'thread-1', 101, ARRAY['INBOX']::text[], ?),
                            (?, 'msg-2', 'thread-2', 102, ARRAY['INBOX']::text[], ?)
                        """,
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:05:00Z")),
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:06:00Z")));
        seedTriageAudit(
                tenantId,
                "msg-1",
                "label",
                "APPLIED",
                Instant.parse("2042-01-01T10:07:00Z"),
                hashBytes(1));
        seedTriageAudit(
                tenantId,
                "msg-2",
                "send_reply",
                "FAILED",
                Instant.parse("2042-01-01T10:08:00Z"),
                hashBytes(2));
        seedTriageAudit(
                tenantId,
                "msg-3",
                "forward_email",
                "REJECTED_BY_SAFETY_NET",
                Instant.parse("2042-01-01T10:09:00Z"),
                hashBytes(3));
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO chat(id, tenant_id, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                chatId,
                tenantId,
                "Support chat",
                Timestamp.from(Instant.parse("2042-01-01T10:10:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:10:00Z")));
        jdbcTemplate.update(
                """
                        INSERT INTO assistant_action_audit(
                            chat_id, tenant_id, tool_call_id, tool_category, tool_name, state,
                            preview_snapshot, sent_at, created_at, updated_at
                        )
                        VALUES (?, ?, 'tool-call-1', 'CONFIRMED_SEND', 'sendEmail', 'COMMITTED',
                                '{}'::jsonb, ?, ?, ?)
                        """,
                chatId,
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:13:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:13:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:13:00Z")));
        seedLlmCall(tenantId, "llm-1", Instant.parse("2042-01-01T10:11:00Z"));
        seedLlmCall(tenantId, "llm-2", Instant.parse("2042-01-01T10:12:00Z"));
        seedCreditEntry(tenantId, "TOPUP", 100, "topup-1", Instant.parse("2042-01-01T10:14:00Z"));
        seedCreditEntry(
                tenantId, "RESERVE", -5, "reserve-1", Instant.parse("2042-01-01T10:15:00Z"));
        jdbcTemplate.update(
                """
                        INSERT INTO pubsub_delivery(
                            id, tenant_id, pubsub_message_id, history_id, payload, status,
                            attempts, created_at, updated_at
                        )
                        VALUES
                            (?, ?, 'pubsub-1', 1001, '{}'::jsonb, 'PENDING', 0, ?, ?),
                            (?, ?, 'pubsub-2', 1002, '{}'::jsonb, 'PENDING', 1, ?, ?)
                        """,
                UUID.randomUUID(),
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:16:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:16:00Z")),
                UUID.randomUUID(),
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:17:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:17:00Z")));
        jdbcTemplate.update(
                """
                        INSERT INTO rule_automation_settings(
                            tenant_id, auto_send_rules_enabled, created_at, updated_at
                        )
                        VALUES (?, false, ?, ?)
                        """,
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:18:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:18:00Z")));
        jdbcTemplate.update(
                """
                        INSERT INTO telegram_account(
                            id, tenant_id, telegram_chat_id, telegram_user_id, telegram_username,
                            language_code, status, notifications_enabled, notification_filter,
                            linked_at, last_active_at, created_at, updated_at, version
                        )
                        VALUES (?, ?, 92001, 93001, 'founder', 'vi', 'CONNECTED',
                                true, '{}'::jsonb, ?, ?, ?, ?, 0)
                        """,
                UUID.randomUUID(),
                tenantId,
                Timestamp.from(Instant.parse("2042-01-01T10:12:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:12:30Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:12:00Z")),
                Timestamp.from(Instant.parse("2042-01-01T10:12:30Z")));
    }

    private void attachCurrentPlanPeriod(UUID tenantId, String planCode) {
        jdbcTemplate.update(
                """
                        INSERT INTO billing_plan_period(
                            id, tenant_id, plan_id, status, provider, provider_order_id,
                            effective_at, expires_at, paid_at, amount_vnd, currency
                        )
                        SELECT ?, ?, billing_plan.id, 'ACTIVE', 'ADMIN', ?,
                               CURRENT_TIMESTAMP - INTERVAL '1 minute',
                               CURRENT_TIMESTAMP + INTERVAL '30 days',
                               CURRENT_TIMESTAMP - INTERVAL '1 minute',
                               0, 'VND'
                        FROM billing_plan
                        WHERE billing_plan.code = ?
                        """,
                UUID.randomUUID(),
                tenantId,
                "admin-test-" + tenantId,
                planCode);
    }

    private void seedDisconnectedTenant(UUID tenantId) {
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name, created_at) VALUES (?, ?, ?)",
                tenantId,
                "disconnected-tenant",
                Timestamp.from(Instant.parse("2042-01-01T09:00:00Z")));
    }

    private void seedTriageAudit(
            UUID tenantId,
            String gmailMessageId,
            String actionType,
            String decision,
            Instant createdAt,
            byte[] argsHash) {
        jdbcTemplate.update(
                """
                        INSERT INTO triage_audit(
                            tenant_id, gmail_message_id, gmail_thread_id, action_type, args_hash,
                            action_args_json, reason, decision, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, '{}'::jsonb, 'test action', ?, ?, ?)
                        """,
                tenantId,
                gmailMessageId,
                "thread-" + gmailMessageId,
                actionType,
                argsHash,
                decision,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private void seedLlmCall(UUID tenantId, String refId, Instant createdAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO llm_call_audit(
                            id, tenant_id, provider, feature, model_id, credential_source,
                            prompt_tokens, completion_tokens, total_cost_usd, call_site,
                            charged_credits, created_at
                        )
                        VALUES (?, ?, 'OPENROUTER', 'TRIAGE', 'openrouter/test-model', 'PLATFORM',
                                10, 5, 0.010000, 'TRIAGE', 1, ?)
                        """,
                UUID.nameUUIDFromBytes(
                        (tenantId + ":" + refId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                tenantId,
                Timestamp.from(createdAt));
    }

    private void seedCreditEntry(
            UUID tenantId, String kind, int amountCredits, String refId, Instant createdAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO credit_ledger_entry(
                            id, tenant_id, kind, amount_credits, ref_type, ref_id, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, 'TEST', ?, ?, ?)
                        """,
                UUID.randomUUID(),
                tenantId,
                kind,
                amountCredits,
                refId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private static byte[] hashBytes(int marker) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) marker;
        return bytes;
    }
}

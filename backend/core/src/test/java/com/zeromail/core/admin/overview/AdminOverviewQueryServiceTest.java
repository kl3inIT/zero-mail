package com.zeromail.core.admin.overview;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.overview.projection.AdminOverviewQuery;
import com.zeromail.core.admin.overview.projection.AdminOverviewSnapshot;
import com.zeromail.core.admin.overview.projection.AdminOverviewTopActivityTenant;
import com.zeromail.core.admin.overview.projection.AdminOverviewTopSpendTenant;
import com.zeromail.core.admin.overview.usecases.AdminOverviewQueryService;
import com.zeromail.core.support.PostgresContainerTest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminOverviewQueryServiceTest extends PostgresContainerTest {

    private static final Instant RANGE_FROM = Instant.parse("2042-02-01T00:00:00Z");
    private static final Instant RANGE_TO = Instant.parse("2042-02-08T00:00:00Z");

    @Autowired private AdminOverviewQueryService adminOverviewQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("TRUNCATE TABLE tenants, gmail_connections RESTART IDENTITY CASCADE");
    }

    @Test
    void snapshot_uses_dedicated_activity_range_instead_of_tenant_created_at() {
        UUID oldActiveTenantId = UUID.fromString("00000000-0000-4000-8000-00000000a101");
        UUID newInactiveTenantId = UUID.fromString("00000000-0000-4000-8000-00000000a102");
        UUID secondaryTenantId = UUID.fromString("00000000-0000-4000-8000-00000000a103");

        UUID oldActiveGmailConnectionId =
                seedTenant(
                        oldActiveTenantId,
                        "Old tenant with real activity",
                        "owner-old@example.com",
                        "old-primary@example.com",
                        Instant.parse("2041-12-15T09:00:00Z"),
                        true);
        UUID newInactiveGmailConnectionId =
                seedTenant(
                        newInactiveTenantId,
                        "New tenant without activity",
                        "owner-new@example.com",
                        "new-primary@example.com",
                        Instant.parse("2042-02-02T09:00:00Z"),
                        true);
        UUID secondaryGmailConnectionId =
                seedTenant(
                        secondaryTenantId,
                        "Secondary active tenant",
                        "owner-secondary@example.com",
                        "secondary-primary@example.com",
                        Instant.parse("2041-12-20T09:00:00Z"),
                        false);

        seedObservedMessages(oldActiveTenantId, oldActiveGmailConnectionId, "old", 3);
        seedObservedMessages(secondaryTenantId, secondaryGmailConnectionId, "secondary", 1);
        seedTriageAudit(
                oldActiveTenantId,
                oldActiveGmailConnectionId,
                "old-message-1",
                "label",
                "APPLIED",
                Instant.parse("2042-02-02T10:30:00Z"));
        seedTriageAudit(
                oldActiveTenantId,
                oldActiveGmailConnectionId,
                "old-message-2",
                "send_reply",
                "FAILED",
                Instant.parse("2042-02-03T10:30:00Z"));
        seedTriageAudit(
                oldActiveTenantId,
                oldActiveGmailConnectionId,
                "old-message-3",
                "forward_email",
                "REJECTED_BY_SAFETY_NET",
                Instant.parse("2042-02-04T10:30:00Z"));
        seedLlmCall(oldActiveTenantId, "old-llm-1", 4, Instant.parse("2042-02-02T11:00:00Z"));
        seedLlmCall(oldActiveTenantId, "old-llm-2", 6, Instant.parse("2042-02-03T11:00:00Z"));
        seedLlmCall(secondaryTenantId, "secondary-llm-1", 2, Instant.parse("2042-02-03T11:00:00Z"));

        // This row proves the overview range applies to activity tables. It must not count even
        // though the tenant itself was created inside the selected range.
        seedObservedMessage(
                newInactiveTenantId,
                newInactiveGmailConnectionId,
                "new-outside-range",
                Instant.parse("2042-01-15T10:00:00Z"));

        AdminOverviewSnapshot snapshot =
                adminOverviewQueryService.snapshot(new AdminOverviewQuery(RANGE_FROM, RANGE_TO));

        assertThat(snapshot.kpis().totalTenants()).isEqualTo(3);
        assertThat(snapshot.kpis().gmailConnectedTenants()).isEqualTo(2);
        assertThat(snapshot.kpis().observedEmailCount()).isEqualTo(4);
        assertThat(snapshot.kpis().triageActionCount()).isEqualTo(3);
        assertThat(snapshot.kpis().failedTriageActionCount()).isEqualTo(1);
        assertThat(snapshot.kpis().blockedOutboundActionCount()).isEqualTo(1);
        assertThat(snapshot.kpis().llmCallCount()).isEqualTo(3);
        assertThat(snapshot.kpis().llmChargedCredits()).isEqualTo(12);

        assertThat(snapshot.topActivityTenants())
                .extracting(AdminOverviewTopActivityTenant::tenantId)
                .containsExactly(oldActiveTenantId, secondaryTenantId);
        AdminOverviewTopActivityTenant oldActiveTenant = snapshot.topActivityTenants().getFirst();
        assertThat(oldActiveTenant.tenantDisplayName()).isEqualTo("Old tenant with real activity");
        assertThat(oldActiveTenant.primaryEmail()).isEqualTo("old-primary@example.com");
        assertThat(oldActiveTenant.observedEmailCount()).isEqualTo(3);
        assertThat(oldActiveTenant.triageActionCount()).isEqualTo(3);
        assertThat(oldActiveTenant.failedTriageActionCount()).isEqualTo(1);
        assertThat(oldActiveTenant.failureRatePercent()).isEqualTo(33.3333);

        assertThat(snapshot.topSpendTenants())
                .extracting(AdminOverviewTopSpendTenant::tenantId)
                .containsExactly(oldActiveTenantId, secondaryTenantId);
        assertThat(snapshot.topSpendTenants().getFirst().chargedCredits()).isEqualTo(10);

        assertThat(snapshot.dailyActivity())
                .anySatisfy(
                        dailyActivityPoint -> {
                            assertThat(dailyActivityPoint.date()).isEqualTo("2042-02-02");
                            assertThat(dailyActivityPoint.observedEmailCount()).isEqualTo(2);
                            assertThat(dailyActivityPoint.triageActionCount()).isEqualTo(1);
                        });
        assertThat(snapshot.actionDistribution())
                .anySatisfy(
                        actionDistribution -> {
                            assertThat(actionDistribution.key()).isEqualTo("FAILED_OR_BLOCKED");
                            assertThat(actionDistribution.count()).isEqualTo(2);
                        });
    }

    private UUID seedTenant(
            UUID tenantId,
            String displayName,
            String ownerEmail,
            String gmailEmail,
            Instant createdAt,
            boolean connected) {
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name, created_at) VALUES (?, ?, ?)",
                tenantId,
                displayName,
                Timestamp.from(createdAt));
        jdbcTemplate.update(
                """
                INSERT INTO users(id, tenant_id, google_subject, email, onboarding_step, created_at)
                VALUES (?, ?, ?, ?, 'GMAIL_CONNECTED', ?)
                """,
                UUID.randomUUID(),
                tenantId,
                "subject-" + tenantId,
                ownerEmail,
                Timestamp.from(createdAt.plusSeconds(30)));
        UUID gmailConnectionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO gmail_connections(
                    id, tenant_id, google_email, status, refresh_token_encrypted, scopes_granted,
                    connected_at, created_at, updated_at, watch_expires_at, is_primary
                )
                VALUES (?, ?, ?, ?, NULL, 'gmail.modify', ?, ?, ?, ?, true)
                """,
                gmailConnectionId,
                tenantId,
                gmailEmail,
                connected ? "CONNECTED" : "DISCONNECTED",
                Timestamp.from(createdAt.plusSeconds(60)),
                Timestamp.from(createdAt.plusSeconds(60)),
                Timestamp.from(createdAt.plusSeconds(60)),
                Timestamp.from(RANGE_TO.plusSeconds(86_400)));
        return gmailConnectionId;
    }

    private void seedObservedMessages(
            UUID tenantId, UUID gmailConnectionId, String messagePrefix, int count) {
        for (int messageIndex = 1; messageIndex <= count; messageIndex++) {
            seedObservedMessage(
                    tenantId,
                    gmailConnectionId,
                    messagePrefix + "-message-" + messageIndex,
                    RANGE_FROM.plusSeconds(messageIndex * 86_400L + 600));
        }
    }

    private void seedObservedMessage(
            UUID tenantId, UUID gmailConnectionId, String messageId, Instant observedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO mail_message_observed(
                    tenant_id, gmail_connection_id, gmail_message_id, gmail_thread_id,
                    history_id, label_ids, observed_at
                )
                VALUES (?, ?, ?, ?, ?, ARRAY['INBOX']::text[], ?)
                """,
                tenantId,
                gmailConnectionId,
                messageId,
                "thread-" + messageId,
                Math.abs(messageId.hashCode()),
                Timestamp.from(observedAt));
    }

    private void seedTriageAudit(
            UUID tenantId,
            UUID gmailConnectionId,
            String messageId,
            String actionType,
            String decision,
            Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO triage_audit(
                    tenant_id, source_mailbox_id, executing_mailbox_id, gmail_message_id,
                    gmail_thread_id, action_type, args_hash,
                    action_args_json, reason, decision, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, 'overview test', ?, ?, ?)
                """,
                tenantId,
                gmailConnectionId,
                gmailConnectionId,
                messageId,
                "thread-" + messageId,
                actionType,
                hashBytes(messageId),
                decision,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private void seedLlmCall(
            UUID tenantId, String callReference, int chargedCredits, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO llm_call_audit(
                    id, tenant_id, provider, feature, model_id, credential_source,
                    prompt_tokens, completion_tokens, total_cost_usd, call_site,
                    charged_credits, created_at
                )
                VALUES (?, ?, 'OPENROUTER', 'TRIAGE', 'openrouter/test-model', 'PLATFORM',
                        100, 50, 0.000000, 'TRIAGE', ?, ?)
                """,
                UUID.nameUUIDFromBytes(callReference.getBytes(StandardCharsets.UTF_8)),
                tenantId,
                chargedCredits,
                Timestamp.from(createdAt));
    }

    private static byte[] hashBytes(String marker) {
        byte[] bytes = new byte[32];
        byte[] markerBytes = marker.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(markerBytes, 0, bytes, 0, Math.min(bytes.length, markerBytes.length));
        return bytes;
    }
}

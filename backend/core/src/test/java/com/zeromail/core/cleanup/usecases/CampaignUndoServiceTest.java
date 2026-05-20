package com.zeromail.core.cleanup.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * UNS-07a — Campaign undo within 30 days stub.
 *
 * <p>Future production class {@code CampaignUndoService} (Wave 4b / Plan 07):
 *
 * <ul>
 *   <li>Within 30 days of {@code unsubscribe_campaign.applied_at}: restore every archived message
 *       to INBOX (via {@code TriageGmailWriter.applyLabel(INBOX)} plus {@code removeLabel("Zero
 *       Mail/Unsubscribed")}) and set {@code reverted_at} on every associated audit row.
 *   <li>After 30 days: throw {@code UndoWindowExpiredException} so the controller can return HTTP
 *       410 with code {@code UNDO_WINDOW_EXPIRED}.
 * </ul>
 *
 * <p>Wave 0 RED: production class not present.
 */
@SuppressWarnings("SqlResolve")
class CampaignUndoServiceTest extends PostgresContainerTest {

    private static final String CAMPAIGN_UNDO_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignUndoService";
    private static final String UNDO_WINDOW_EXPIRED_EXCEPTION =
            "com.zeromail.core.cleanup.exception.UndoWindowExpiredException";
    private static final String UNSUBSCRIBED_LABEL_NAME = "Zero Mail/Unsubscribed";

    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean TriageGmailWriter triageGmailWriter;

    @Test
    void future_undo_service_types_are_present() {
        assertThatCode(() -> Class.forName(CAMPAIGN_UNDO_SERVICE))
                .as("Future production type must exist: " + CAMPAIGN_UNDO_SERVICE)
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(UNDO_WINDOW_EXPIRED_EXCEPTION))
                .as("Future exception type must exist: " + UNDO_WINDOW_EXPIRED_EXCEPTION)
                .doesNotThrowAnyException();
    }

    @Test
    void undoWithin30Days_restoresInboxAndRemovesLabel() throws Exception {
        Class.forName(CAMPAIGN_UNDO_SERVICE);
        UUID tenantId = seedTenant();
        UUID campaignId = seedCampaign(tenantId, Instant.parse("2026-05-01T00:00:00Z"));
        seedArchivedMessage(tenantId, campaignId, "gmail-msg-1");
        seedArchivedMessage(tenantId, campaignId, "gmail-msg-2");

        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        Object campaignUndoService = lookupUndoBean(fixedClock);
        withTenant(tenantId, () -> invokeUndo(campaignUndoService, tenantId, campaignId));

        verify(triageGmailWriter, atLeastOnce()).applyLabel(tenantId, "gmail-msg-1", "INBOX");
        verify(triageGmailWriter, atLeastOnce()).applyLabel(tenantId, "gmail-msg-2", "INBOX");
        verify(triageGmailWriter, atLeastOnce())
                .removeLabel(tenantId, "gmail-msg-1", UNSUBSCRIBED_LABEL_NAME);
        verify(triageGmailWriter, atLeastOnce())
                .removeLabel(tenantId, "gmail-msg-2", UNSUBSCRIBED_LABEL_NAME);
    }

    @Test
    void undoSetsRevertedAtOnAllAuditRows() throws Exception {
        Class.forName(CAMPAIGN_UNDO_SERVICE);
        UUID tenantId = seedTenant();
        UUID campaignId = seedCampaign(tenantId, Instant.parse("2026-05-01T00:00:00Z"));
        seedArchivedMessage(tenantId, campaignId, "gmail-msg-revert-1");
        seedArchivedMessage(tenantId, campaignId, "gmail-msg-revert-2");
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-10T00:00:00Z"), ZoneOffset.UTC);

        Object campaignUndoService = lookupUndoBean(fixedClock);
        withTenant(tenantId, () -> invokeUndo(campaignUndoService, tenantId, campaignId));

        Long revertedRowCount =
                jdbcTemplate.queryForObject(
                        """
                        select count(*) from triage_audit
                        where tenant_id = ? and reverted_at is not null
                          and gmail_message_id in (?, ?)
                        """,
                        Long.class,
                        tenantId,
                        "gmail-msg-revert-1",
                        "gmail-msg-revert-2");
        assertThat(revertedRowCount)
                .as("every audit row tied to the campaign must have reverted_at set")
                .isEqualTo(2L);
    }

    @Test
    void undoAfter30Days_throwsUndoWindowExpired() throws Exception {
        Class.forName(CAMPAIGN_UNDO_SERVICE);
        Class.forName(UNDO_WINDOW_EXPIRED_EXCEPTION);
        UUID tenantId = seedTenant();
        UUID campaignId = seedCampaign(tenantId, Instant.parse("2026-01-01T00:00:00Z"));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);

        Object campaignUndoService = lookupUndoBean(fixedClock);

        assertThatThrownBy(
                        () ->
                                withTenant(
                                        tenantId,
                                        () ->
                                                invokeUndo(
                                                        campaignUndoService, tenantId, campaignId)))
                .as("undo past 30-day window must throw UndoWindowExpiredException")
                .hasRootCauseInstanceOf(
                        Class.forName(UNDO_WINDOW_EXPIRED_EXCEPTION)
                                .asSubclass(RuntimeException.class));
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "campaign-undo-" + tenantId);
        return tenantId;
    }

    private UUID seedCampaign(UUID tenantId, Instant appliedAt) {
        UUID campaignId = UUID.randomUUID();
        UUID processingJobId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into processing_job(
                    id, tenant_id, job_type, payload, status, attempts, created_at, finished_at)
                values (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                """,
                processingJobId,
                tenantId,
                "UNSUBSCRIBE_CAMPAIGN",
                "{\"campaignId\":\"" + campaignId + "\"}",
                "COMPLETED",
                1,
                appliedAt,
                appliedAt);
        jdbcTemplate.update(
                """
                insert into unsubscribe_campaign(
                    id, tenant_id, job_id, status, applied_at, total_sender, total_history_msg,
                    created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                campaignId,
                tenantId,
                processingJobId,
                "COMPLETED",
                appliedAt,
                2,
                2,
                appliedAt);
        return campaignId;
    }

    private void seedArchivedMessage(UUID tenantId, UUID campaignId, String gmailMessageId) {
        jdbcTemplate.update(
                """
                insert into triage_audit(
                    audit_id, tenant_id, gmail_message_id, gmail_thread_id, subject_excerpt,
                    sanitized_sender_email, rule_id, reason, action_type, args_hash,
                    action_args_json, matcher_evidence, decision, created_at, decided_at,
                    applied_at, attempt_count, external_ref)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                gmailMessageId,
                "thread-" + gmailMessageId,
                "Subject",
                "sender@example.com",
                null,
                "campaign archive",
                "ARCHIVE",
                new byte[32],
                "{\"type\":\"archive\"}",
                "campaign:" + campaignId,
                "APPLIED",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                1,
                campaignId.toString());
    }

    private Object lookupUndoBean(Clock fixedClock) throws Exception {
        return Class.forName(CAMPAIGN_UNDO_SERVICE)
                .getDeclaredConstructor(JdbcTemplate.class, TriageGmailWriter.class, Clock.class)
                .newInstance(jdbcTemplate, triageGmailWriter, fixedClock);
    }

    private static Object invokeUndo(Object campaignUndoService, UUID tenantId, UUID campaignId) {
        try {
            return campaignUndoService
                    .getClass()
                    .getMethod("undo", UUID.class, UUID.class)
                    .invoke(campaignUndoService, tenantId, campaignId);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
    }

    private static void withTenant(UUID tenantId, Runnable runnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(runnable);
    }
}

package com.zeromail.core.cleanup.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * UNS-07 — Campaign undo within the 30-day window restores INBOX, removes the {@code Zero
 * Mail/Unsubscribed} label, and marks {@code triage_audit.reverted_at} on every {@code
 * source='CLEANUP_CAMPAIGN'} row.
 *
 * <p>The production class {@code CampaignUndoService} (Plan 07 Task 3) accepts a 3-arg constructor
 * {@code (JdbcTemplate, TriageGmailWriter, Clock)} and exposes {@code undo(UUID tenantId, UUID
 * campaignId)}. {@link TriageGmailWriter} is {@code @MockitoBean}-stubbed so the test verifies
 * invocation contracts without requiring real Gmail credentials.
 *
 * <p>H-3 precise undo: the production service queries {@code triage_audit} with {@code source =
 * 'CLEANUP_CAMPAIGN'} (changelog 046) so pre-existing rule-driven {@code source='TRIAGE'} audit
 * rows for the same sender are NOT touched. {@link #undoDoesNotTouchTriageSourcedAuditRows()} pins
 * this invariant.
 */
@SuppressWarnings("SqlResolve")
class CampaignUndoServiceTest extends PostgresContainerTest {

    private static final String CAMPAIGN_UNDO_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignUndoService";
    private static final String UNDO_WINDOW_EXPIRED_EXCEPTION =
            "com.zeromail.core.cleanup.exception.UndoWindowExpiredException";
    private static final String UNSUBSCRIBED_LABEL_NAME = "Zero Mail/Unsubscribed";
    private static final String UNSUBSCRIBED_LABEL_ID = "Label_42";

    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean TriageGmailWriter triageGmailWriter;

    @Test
    void future_undo_service_types_are_present() {
        assertThatCode(() -> Class.forName(CAMPAIGN_UNDO_SERVICE))
                .as("Production type must exist: " + CAMPAIGN_UNDO_SERVICE)
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(UNDO_WINDOW_EXPIRED_EXCEPTION))
                .as("Exception type must exist: " + UNDO_WINDOW_EXPIRED_EXCEPTION)
                .doesNotThrowAnyException();
    }

    @Test
    void undoWithin30Days_restoresInboxAndRemovesLabel() throws Exception {
        Class.forName(CAMPAIGN_UNDO_SERVICE);
        UUID tenantId = seedTenant();
        UUID campaignId = seedCampaign(tenantId, Instant.parse("2026-05-01T00:00:00Z"));
        seedCleanupArchive(tenantId, campaignId, "gmail-msg-1");
        seedCleanupArchive(tenantId, campaignId, "gmail-msg-2");
        stubLookupLabelId(tenantId, Optional.of(UNSUBSCRIBED_LABEL_ID));

        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);
        Object campaignUndoService = newUndoService(fixedClock);
        withTenant(tenantId, () -> invokeUndo(campaignUndoService, tenantId, campaignId));

        verify(triageGmailWriter, atLeastOnce()).restoreToInbox(tenantId, "gmail-msg-1");
        verify(triageGmailWriter, atLeastOnce()).restoreToInbox(tenantId, "gmail-msg-2");
        verify(triageGmailWriter, atLeastOnce())
                .removeLabel(tenantId, "gmail-msg-1", UNSUBSCRIBED_LABEL_ID);
        verify(triageGmailWriter, atLeastOnce())
                .removeLabel(tenantId, "gmail-msg-2", UNSUBSCRIBED_LABEL_ID);
    }

    @Test
    void undoSetsRevertedAtOnAllAuditRows() throws Exception {
        Class.forName(CAMPAIGN_UNDO_SERVICE);
        UUID tenantId = seedTenant();
        UUID campaignId = seedCampaign(tenantId, Instant.parse("2026-05-01T00:00:00Z"));
        seedCleanupArchive(tenantId, campaignId, "gmail-msg-revert-1");
        seedCleanupArchive(tenantId, campaignId, "gmail-msg-revert-2");
        stubLookupLabelId(tenantId, Optional.of(UNSUBSCRIBED_LABEL_ID));

        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-10T00:00:00Z"), ZoneOffset.UTC);
        Object campaignUndoService = newUndoService(fixedClock);
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

        Long campaignRevertedCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from unsubscribe_campaign "
                                + "where id = ? and reverted_at is not null",
                        Long.class,
                        campaignId);
        assertThat(campaignRevertedCount)
                .as("campaign row must have reverted_at set")
                .isEqualTo(1L);
    }

    @Test
    void undoAfter30Days_throwsUndoWindowExpired() throws Exception {
        Class.forName(CAMPAIGN_UNDO_SERVICE);
        Class.forName(UNDO_WINDOW_EXPIRED_EXCEPTION);
        UUID tenantId = seedTenant();
        UUID campaignId = seedCampaign(tenantId, Instant.parse("2026-01-01T00:00:00Z"));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneOffset.UTC);

        Object campaignUndoService = newUndoService(fixedClock);

        assertThatThrownBy(
                        () ->
                                withTenant(
                                        tenantId,
                                        () ->
                                                invokeUndo(
                                                        campaignUndoService, tenantId, campaignId)))
                .as("undo past 30-day window must throw UndoWindowExpiredException")
                .isInstanceOf(
                        Class.forName(UNDO_WINDOW_EXPIRED_EXCEPTION)
                                .asSubclass(RuntimeException.class));
    }

    @Test
    void undoDoesNotTouchTriageSourcedAuditRows() throws Exception {
        Class.forName(CAMPAIGN_UNDO_SERVICE);
        UUID tenantId = seedTenant();
        UUID campaignId = seedCampaign(tenantId, Instant.parse("2026-05-01T00:00:00Z"));
        seedCleanupArchive(tenantId, campaignId, "gmail-msg-cleanup-1");
        // H-3 false-positive guard: a TRIAGE-sourced row for the SAME sender + same external_ref
        // payload must NOT be reverted by cleanup undo.
        seedTriageArchive(tenantId, campaignId, "gmail-msg-triage-1");
        stubLookupLabelId(tenantId, Optional.of(UNSUBSCRIBED_LABEL_ID));

        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-10T00:00:00Z"), ZoneOffset.UTC);
        Object campaignUndoService = newUndoService(fixedClock);
        withTenant(tenantId, () -> invokeUndo(campaignUndoService, tenantId, campaignId));

        Long cleanupRevertedCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from triage_audit "
                                + "where tenant_id = ? and source = 'CLEANUP_CAMPAIGN' "
                                + "and reverted_at is not null",
                        Long.class,
                        tenantId);
        assertThat(cleanupRevertedCount)
                .as("only CLEANUP_CAMPAIGN rows should be reverted")
                .isEqualTo(1L);

        Long triageRevertedCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from triage_audit "
                                + "where tenant_id = ? and source = 'TRIAGE' "
                                + "and reverted_at is not null",
                        Long.class,
                        tenantId);
        assertThat(triageRevertedCount)
                .as("TRIAGE rows must not be touched by cleanup undo (H-3)")
                .isEqualTo(0L);

        verify(triageGmailWriter, never()).restoreToInbox(tenantId, "gmail-msg-triage-1");
    }

    @Test
    void undoTolerates_userDeletedLabelInGmail() throws Exception {
        Class.forName(CAMPAIGN_UNDO_SERVICE);
        UUID tenantId = seedTenant();
        UUID campaignId = seedCampaign(tenantId, Instant.parse("2026-05-01T00:00:00Z"));
        seedCleanupArchive(tenantId, campaignId, "gmail-msg-tolerant-1");
        // User deleted "Zero Mail/Unsubscribed" in Gmail between apply and undo — lookupLabelId
        // returns empty and undo must still restore INBOX, just skipping the removeLabel step.
        stubLookupLabelId(tenantId, Optional.empty());

        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-10T00:00:00Z"), ZoneOffset.UTC);
        Object campaignUndoService = newUndoService(fixedClock);
        withTenant(tenantId, () -> invokeUndo(campaignUndoService, tenantId, campaignId));

        verify(triageGmailWriter, atLeastOnce()).restoreToInbox(tenantId, "gmail-msg-tolerant-1");
        verify(triageGmailWriter, never()).removeLabel(eq(tenantId), any(), any());
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
        Timestamp appliedAtTimestamp = Timestamp.from(appliedAt);
        jdbcTemplate.update(
                """
                insert into processing_job(
                    id, tenant_id, job_type, payload, status, attempts,
                    next_run_at, created_at, finished_at, updated_at, version)
                values (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                """,
                processingJobId,
                tenantId,
                "UNSUBSCRIBE_CAMPAIGN",
                "{\"campaignId\":\"" + campaignId + "\",\"schemaVersion\":1}",
                "COMPLETED",
                1,
                appliedAtTimestamp,
                appliedAtTimestamp,
                appliedAtTimestamp,
                appliedAtTimestamp,
                0);
        jdbcTemplate.update(
                """
                insert into unsubscribe_campaign(
                    id, tenant_id, job_id, status, applied_at,
                    total_sender_count, total_history_message_count,
                    created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                campaignId,
                tenantId,
                processingJobId,
                "COMPLETED",
                appliedAtTimestamp,
                2,
                2,
                appliedAtTimestamp,
                appliedAtTimestamp,
                0);
        return campaignId;
    }

    private void seedCleanupArchive(UUID tenantId, UUID campaignId, String gmailMessageId) {
        Timestamp nowTimestamp = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into triage_audit(
                    audit_id, tenant_id, gmail_message_id, gmail_thread_id, sanitized_subject,
                    sanitized_sender_email, rule_id, reason, action_type, args_hash,
                    action_args_json, decision, created_at, decided_at, applied_at,
                    attempt_count, external_ref, source, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                "APPLIED",
                nowTimestamp,
                nowTimestamp,
                nowTimestamp,
                1,
                campaignId.toString(),
                "CLEANUP_CAMPAIGN",
                nowTimestamp,
                0);
    }

    private void seedTriageArchive(UUID tenantId, UUID campaignId, String gmailMessageId) {
        // Identical shape to seedCleanupArchive but source='TRIAGE' — must NOT be touched by undo.
        // Different args_hash so the (tenant, message, rule, action, args_hash) unique key
        // tolerates
        // the dual insert.
        byte[] differentArgsHash = new byte[32];
        differentArgsHash[0] = 1;
        Timestamp nowTimestamp = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into triage_audit(
                    audit_id, tenant_id, gmail_message_id, gmail_thread_id, sanitized_subject,
                    sanitized_sender_email, rule_id, reason, action_type, args_hash,
                    action_args_json, decision, created_at, decided_at, applied_at,
                    attempt_count, external_ref, source, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                gmailMessageId,
                "thread-" + gmailMessageId,
                "Subject",
                "sender@example.com",
                null,
                "triage archive",
                "ARCHIVE",
                differentArgsHash,
                "{\"type\":\"archive\"}",
                "APPLIED",
                nowTimestamp,
                nowTimestamp,
                nowTimestamp,
                1,
                campaignId.toString(),
                "TRIAGE",
                nowTimestamp,
                0);
    }

    private void stubLookupLabelId(UUID tenantId, Optional<String> labelId) throws Exception {
        when(triageGmailWriter.lookupLabelId(tenantId, UNSUBSCRIBED_LABEL_NAME))
                .thenReturn(labelId);
    }

    private Object newUndoService(Clock fixedClock) throws Exception {
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
            // Unwrap InvocationTargetException so assertThatThrownBy can see the real cause.
            Throwable cause = reflectiveOperationException.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(reflectiveOperationException);
        }
    }

    private static void withTenant(UUID tenantId, Runnable runnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(runnable);
    }
}

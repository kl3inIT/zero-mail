package com.zeromail.core.inbox.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.inbox.domain.InboxSyncStatus;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateId;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies enqueue idempotency: even when called repeatedly while a job is open (PENDING or
 * PROCESSING) the enqueuer must not stack duplicate rows on the {@code processing_job} table. This
 * is the dedup contract the trigger callers (OAuth provisioning + lazy fetchPage) rely on.
 */
class InboxBackfillEnqueuerTest extends PostgresContainerTest {

    @Autowired InboxBackfillEnqueuer inboxBackfillEnqueuer;
    @Autowired GmailInboxSyncStateRepository syncStateRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void first_call_creates_processing_job_and_marks_sync_state_BACKFILLING() {
        MailboxRef mailboxRef = new MailboxRef(seedTenant(), UUID.randomUUID());

        boolean enqueued =
                ScopedValue.where(TenantContext.TENANT, mailboxRef.tenantId().toString())
                        .call(() -> inboxBackfillEnqueuer.enqueueIfNotPending(mailboxRef));

        assertThat(enqueued).isTrue();
        assertOpenJobCount(mailboxRef).isEqualTo(1);
        assertThat(backfillPayload(mailboxRef))
                .isEqualTo("{\"gmailConnectionId\": \"" + mailboxRef.gmailConnectionId() + "\"}");
        assertThat(
                        ScopedValue.where(TenantContext.TENANT, mailboxRef.tenantId().toString())
                                .call(
                                        () ->
                                                syncStateRepository
                                                        .findById(
                                                                new GmailInboxSyncStateId(
                                                                        mailboxRef.tenantId(),
                                                                        mailboxRef
                                                                                .gmailConnectionId()))
                                                        .orElseThrow()
                                                        .getStatus()))
                .isEqualTo(InboxSyncStatus.BACKFILLING);
    }

    @Test
    void repeated_call_while_job_is_PENDING_does_not_insert_duplicate_for_same_mailbox() {
        MailboxRef mailboxRef = new MailboxRef(seedTenant(), UUID.randomUUID());

        ScopedValue.where(TenantContext.TENANT, mailboxRef.tenantId().toString())
                .run(
                        () -> {
                            inboxBackfillEnqueuer.enqueueIfNotPending(mailboxRef);
                            inboxBackfillEnqueuer.enqueueIfNotPending(mailboxRef);
                            inboxBackfillEnqueuer.enqueueIfNotPending(mailboxRef);
                        });

        assertOpenJobCount(mailboxRef).isEqualTo(1);
    }

    @Test
    void different_mailboxes_can_have_concurrent_open_backfills() {
        UUID tenantId = seedTenant();
        MailboxRef firstMailboxRef = new MailboxRef(tenantId, UUID.randomUUID());
        MailboxRef secondMailboxRef = new MailboxRef(tenantId, UUID.randomUUID());

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            inboxBackfillEnqueuer.enqueueIfNotPending(firstMailboxRef);
                            inboxBackfillEnqueuer.enqueueIfNotPending(secondMailboxRef);
                        });

        assertOpenJobCount(firstMailboxRef).isEqualTo(1);
        assertOpenJobCount(secondMailboxRef).isEqualTo(1);
        assertTenantOpenJobCount(tenantId).isEqualTo(2);
    }

    @Test
    void second_call_after_job_COMPLETED_does_insert_new_row() {
        MailboxRef mailboxRef = new MailboxRef(seedTenant(), UUID.randomUUID());

        ScopedValue.where(TenantContext.TENANT, mailboxRef.tenantId().toString())
                .run(() -> inboxBackfillEnqueuer.enqueueIfNotPending(mailboxRef));
        // Simulate worker completion.
        jdbcTemplate.update(
                "UPDATE processing_job SET status = 'COMPLETED', completed_at = NOW() "
                        + "WHERE tenant_id = ? AND gmail_connection_id = ? AND job_type = ?",
                mailboxRef.tenantId(),
                mailboxRef.gmailConnectionId(),
                InboxBackfillEnqueuer.JOB_TYPE);

        boolean reEnqueued =
                ScopedValue.where(TenantContext.TENANT, mailboxRef.tenantId().toString())
                        .call(() -> inboxBackfillEnqueuer.enqueueIfNotPending(mailboxRef));

        assertThat(reEnqueued).isTrue();
        assertOpenJobCount(mailboxRef).isEqualTo(1);
    }

    private org.assertj.core.api.AbstractIntegerAssert<?> assertOpenJobCount(
            MailboxRef mailboxRef) {
        Integer openCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM processing_job "
                                + "WHERE tenant_id = ? AND gmail_connection_id = ? AND job_type = ? "
                                + "  AND status IN ('PENDING', 'PROCESSING')",
                        Integer.class,
                        mailboxRef.tenantId(),
                        mailboxRef.gmailConnectionId(),
                        InboxBackfillEnqueuer.JOB_TYPE);
        return assertThat(openCount);
    }

    private org.assertj.core.api.AbstractIntegerAssert<?> assertTenantOpenJobCount(UUID tenantId) {
        Integer openCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM processing_job "
                                + "WHERE tenant_id = ? AND job_type = ? "
                                + "  AND status IN ('PENDING', 'PROCESSING')",
                        Integer.class,
                        tenantId,
                        InboxBackfillEnqueuer.JOB_TYPE);
        return assertThat(openCount);
    }

    private String backfillPayload(MailboxRef mailboxRef) {
        return jdbcTemplate.queryForObject(
                "SELECT payload_json::text FROM processing_job "
                        + "WHERE tenant_id = ? AND gmail_connection_id = ? AND job_type = ?",
                String.class,
                mailboxRef.tenantId(),
                mailboxRef.gmailConnectionId(),
                InboxBackfillEnqueuer.JOB_TYPE);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        return tenantId;
    }
}

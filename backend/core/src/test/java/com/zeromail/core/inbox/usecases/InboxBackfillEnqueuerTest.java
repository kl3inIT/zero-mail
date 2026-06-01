package com.zeromail.core.inbox.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.inbox.domain.InboxSyncStatus;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies enqueue idempotency: even when called repeatedly while a job is open (PENDING or
 * PROCESSING) the enqueuer must not stack duplicate rows on the {@code processing_job} table.
 * This is the dedup contract the trigger callers (OAuth provisioning + lazy fetchPage) rely on.
 */
class InboxBackfillEnqueuerTest extends PostgresContainerTest {

    @Autowired InboxBackfillEnqueuer inboxBackfillEnqueuer;
    @Autowired GmailInboxSyncStateRepository syncStateRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void first_call_creates_processing_job_and_marks_sync_state_BACKFILLING() {
        UUID tenantId = seedTenant();

        boolean enqueued =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxBackfillEnqueuer.enqueueIfNotPending(tenantId));

        assertThat(enqueued).isTrue();
        assertOpenJobCount(tenantId).isEqualTo(1);
        assertThat(
                        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                .call(
                                        () ->
                                                syncStateRepository
                                                        .findById(tenantId)
                                                        .orElseThrow()
                                                        .getStatus()))
                .isEqualTo(InboxSyncStatus.BACKFILLING);
    }

    @Test
    void repeated_call_while_job_is_PENDING_does_not_insert_duplicate() {
        UUID tenantId = seedTenant();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            inboxBackfillEnqueuer.enqueueIfNotPending(tenantId);
                            inboxBackfillEnqueuer.enqueueIfNotPending(tenantId);
                            inboxBackfillEnqueuer.enqueueIfNotPending(tenantId);
                        });

        assertOpenJobCount(tenantId).isEqualTo(1);
    }

    @Test
    void second_call_after_job_COMPLETED_does_insert_new_row() {
        UUID tenantId = seedTenant();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> inboxBackfillEnqueuer.enqueueIfNotPending(tenantId));
        // Simulate worker completion.
        jdbcTemplate.update(
                "UPDATE processing_job SET status = 'COMPLETED', completed_at = NOW() "
                        + "WHERE tenant_id = ? AND job_type = ?",
                tenantId,
                InboxBackfillEnqueuer.JOB_TYPE);

        boolean reEnqueued =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxBackfillEnqueuer.enqueueIfNotPending(tenantId));

        assertThat(reEnqueued).isTrue();
        assertOpenJobCount(tenantId).isEqualTo(1);
    }

    private org.assertj.core.api.AbstractIntegerAssert<?> assertOpenJobCount(UUID tenantId) {
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

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        return tenantId;
    }
}

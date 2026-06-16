package com.zeromail.core.inbox.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.inbox.domain.InboxSyncStatus;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class GmailInboxSyncStateRepositoryTest extends PostgresContainerTest {

    @Autowired GmailInboxSyncStateRepository syncStateRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void upsertStatus_creates_row_when_missing_and_flips_value_on_repeat() {
        UUID tenantId = seedTenant();
        UUID gmailConnectionId = UUID.randomUUID();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            syncStateRepository.upsertStatus(
                                    tenantId, gmailConnectionId, InboxSyncStatus.BACKFILLING.id());
                            syncStateRepository.upsertStatus(
                                    tenantId, gmailConnectionId, InboxSyncStatus.IDLE.id());
                        });

        GmailInboxSyncStateEntity syncState = findSyncState(tenantId, gmailConnectionId);

        assertThat(syncState.getStatus()).isEqualTo(InboxSyncStatus.IDLE);
        assertThat(syncState.getVersion()).isEqualTo(1);
    }

    @Test
    void recordBackfillSuccess_sets_idle_and_advances_cursor_and_resets_errors() {
        UUID tenantId = seedTenant();
        UUID gmailConnectionId = UUID.randomUUID();
        Instant syncedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            // Seed with a prior error so we observe the reset.
                            syncStateRepository.recordBackfillFailure(
                                    tenantId, gmailConnectionId, Instant.now(), "GMAIL_5XX");
                            syncStateRepository.recordBackfillSuccess(
                                    tenantId, gmailConnectionId, 99999L, syncedAt);
                        });

        GmailInboxSyncStateEntity syncState = findSyncState(tenantId, gmailConnectionId);

        assertThat(syncState.getStatus()).isEqualTo(InboxSyncStatus.IDLE);
        assertThat(syncState.getLastHistoryId()).isEqualTo(99999L);
        assertThat(syncState.getLastFullSyncAt()).isEqualTo(syncedAt);
        assertThat(syncState.getConsecutiveErrors()).isZero();
        assertThat(syncState.getLastErrorAt()).isNull();
        assertThat(syncState.getLastErrorCode()).isNull();
    }

    @Test
    void recordBackfillFailure_increments_consecutive_errors_and_records_code() {
        UUID tenantId = seedTenant();
        UUID gmailConnectionId = UUID.randomUUID();
        Instant firstFailureAt = Instant.now().minusSeconds(60);
        Instant secondFailureAt = Instant.now();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            syncStateRepository.recordBackfillFailure(
                                    tenantId, gmailConnectionId, firstFailureAt, "GMAIL_5XX");
                            syncStateRepository.recordBackfillFailure(
                                    tenantId, gmailConnectionId, secondFailureAt, "GMAIL_TIMEOUT");
                        });

        GmailInboxSyncStateEntity syncState = findSyncState(tenantId, gmailConnectionId);

        assertThat(syncState.getStatus()).isEqualTo(InboxSyncStatus.ERROR);
        assertThat(syncState.getConsecutiveErrors()).isEqualTo(2);
        assertThat(syncState.getLastErrorCode()).isEqualTo("GMAIL_TIMEOUT");
    }

    private GmailInboxSyncStateEntity findSyncState(UUID tenantId, UUID gmailConnectionId) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                syncStateRepository
                                        .findById(
                                                new GmailInboxSyncStateId(
                                                        tenantId, gmailConnectionId))
                                        .orElseThrow());
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

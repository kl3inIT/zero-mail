package com.zeromail.core.gmail.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxPage;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableException;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableReason;
import com.zeromail.core.inbox.domain.InboxProjectionDataSource;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository;
import com.zeromail.core.inbox.usecases.InboxProjectionUpsertCommand;
import com.zeromail.core.inbox.usecases.InboxProjectionWriteService;
import com.zeromail.core.mailbox.MailboxContext;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Postgres-backed integration test for the three branches of the Phase B Wave 1 read orchestrator
 * in {@link RecentInboxReadService#fetchPage}: PROJECTION, first-connect LIVE_GMAIL, and projection
 * short-page LIVE_GMAIL.
 */
class RecentInboxReadServiceFallbackTest extends PostgresContainerTest {

    @Autowired RecentInboxReadService recentInboxReadService;
    @Autowired InboxProjectionWriteService inboxProjectionWriteService;
    @Autowired GmailInboxSyncStateRepository inboxSyncStateRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void firstPage_syncReady_projectionFullPage_returnsProjection() {
        UUID tenantId = seedTenant();
        UUID gmailConnectionId = seedConnectedMailbox(tenantId);
        markSyncReady(tenantId, gmailConnectionId);
        seedProjectionRows(tenantId, gmailConnectionId, 20);

        RecentInboxPage page =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .where(MailboxContext.MAILBOX, gmailConnectionId)
                        .call(() -> recentInboxReadService.fetchPage(tenantId, null, 20));

        assertThat(page.dataSource()).isEqualTo(InboxProjectionDataSource.PROJECTION);
        assertThat(page.messages()).hasSize(20);
        assertThat(page.nextCursor())
                .as("a full projection page must surface a P-prefixed cursor for the next page")
                .isNotNull()
                .startsWith("P");
    }

    @Test
    void firstPage_noSyncStateRow_fallsBackToLiveGmail() {
        UUID tenantId = seedTenant();
        // Just-connected tenant: no sync_state row. First connect now serves the live Gmail first
        // page (the background backfill runs separately) instead of an empty SYNCING banner. With
        // no GmailConnection row, the live path surfaces NOT_CONNECTED.
        assertThatThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                        .call(
                                                () ->
                                                        recentInboxReadService.fetchPage(
                                                                tenantId, null, 20)))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.NOT_CONNECTED);
    }

    @Test
    void firstPage_syncReady_lastFullSyncAtPresentButProjectionEmpty_fallsBackToLiveGmail() {
        UUID tenantId = seedTenant();
        UUID gmailConnectionId = seedConnectedMailbox(tenantId);
        markSyncReady(tenantId, gmailConnectionId);
        // Projection has zero rows; sync_state says ready, so the orchestrator must fall back to
        // live Gmail. The seeded connection intentionally has no refresh token, so live Gmail
        // signals
        // NO_READ_GRANT after reaching that path.
        assertThatThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                        .where(MailboxContext.MAILBOX, gmailConnectionId)
                                        .call(
                                                () ->
                                                        recentInboxReadService.fetchPage(
                                                                tenantId, null, 20)))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.DISCONNECTED,
                        "projection-empty + sync_state ready must trigger the live Gmail fallback;"
                                + " the tokenless seeded mailbox then signals DISCONNECTED");
    }

    @Test
    void firstPage_syncReady_projectionShortPage_fallsBackToLiveGmail() {
        UUID tenantId = seedTenant();
        UUID gmailConnectionId = seedConnectedMailbox(tenantId);
        markSyncReady(tenantId, gmailConnectionId);
        seedProjectionRows(tenantId, gmailConnectionId, 5); // < pageLimit of 20

        assertThatThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                        .where(MailboxContext.MAILBOX, gmailConnectionId)
                                        .call(
                                                () ->
                                                        recentInboxReadService.fetchPage(
                                                                tenantId, null, 20)))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.DISCONNECTED,
                        "short projection page must NOT be returned as PROJECTION; the live fallback"
                                + " then signals DISCONNECTED for the tokenless mailbox");
    }

    private void markSyncReady(UUID tenantId, UUID gmailConnectionId) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                inboxSyncStateRepository.recordBackfillSuccess(
                                        tenantId,
                                        gmailConnectionId,
                                        999_999L,
                                        Instant.parse("2026-05-01T00:00:00Z")));
    }

    private void seedProjectionRows(UUID tenantId, UUID gmailConnectionId, int count) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            for (int rowIndex = 0; rowIndex < count; rowIndex++) {
                                inboxProjectionWriteService.upsert(
                                        new InboxProjectionUpsertCommand(
                                                tenantId,
                                                gmailConnectionId,
                                                String.format("190000000000ff%02x", rowIndex),
                                                "thread-" + rowIndex,
                                                "sender-" + rowIndex + "@example.com",
                                                "Sender " + rowIndex,
                                                "Subject " + rowIndex,
                                                "Snippet " + rowIndex,
                                                false,
                                                Instant.parse("2026-05-01T12:00:00Z")
                                                        .minusSeconds(rowIndex),
                                                List.of("INBOX", "UNREAD"),
                                                400L + rowIndex));
                            }
                        });
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        return tenantId;
    }

    private UUID seedConnectedMailbox(UUID tenantId) {
        UUID gmailConnectionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO gmail_connections(id, tenant_id, google_email, status, is_primary)
                VALUES (?, ?, ?, 'CONNECTED', true)
                """,
                gmailConnectionId,
                tenantId,
                "fallback-" + gmailConnectionId + "@example.test");
        return gmailConnectionId;
    }
}

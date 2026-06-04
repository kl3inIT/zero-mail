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
 * in {@link RecentInboxReadService#fetchPage}: PROJECTION (full page from DB), SYNCING (first fetch
 * before backfill completes), LIVE_GMAIL (projection short → fall back to Gmail).
 *
 * <p>The LIVE_GMAIL branch is asserted via the {@code NOT_CONNECTED} signal — when no Gmail
 * connection row exists for the tenant, the fallback {@code gmailForTenant} call throws and the
 * orchestrator wraps it. Reaching that exception proves the orchestrator transitioned past the
 * projection short-page check and into the live Gmail code path. The full Gmail SDK exchange itself
 * is covered by {@code RecentInboxReadServiceTest} and {@code RecentInboxReadServiceOrches-
 * tratorTest} — duplicating that mock setup here would not increase coverage of the wiring.
 *
 * <p>Lazy backfill enqueue (Phase A wave 3) is still triggered on every fetchPage; the test only
 * cares that the routing decision is correct, not how the enqueue propagates.
 */
class RecentInboxReadServiceFallbackTest extends PostgresContainerTest {

    @Autowired RecentInboxReadService recentInboxReadService;
    @Autowired InboxProjectionWriteService inboxProjectionWriteService;
    @Autowired GmailInboxSyncStateRepository inboxSyncStateRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void firstPage_syncReady_projectionFullPage_returnsProjection() {
        UUID tenantId = seedTenant();
        markSyncReady(tenantId);
        seedProjectionRows(tenantId, 20);

        RecentInboxPage page =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
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
        // no
        // GmailConnection row, the live path surfaces NOT_CONNECTED — reaching it proves the live
        // branch was taken rather than a SYNCING short-circuit.
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
        markSyncReady(tenantId);
        // Projection has zero rows; sync_state says ready → orchestrator must fall back to live
        // Gmail. Since no GmailConnection row exists for this tenant, gmailForTenant throws
        // NOT_CONNECTED — reaching that exception proves the fallback path was taken.

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
                                        == RecentInboxUnavailableReason.NOT_CONNECTED,
                        "projection-empty + sync_state ready must trigger the live Gmail fallback;"
                                + " absent connection then signals NOT_CONNECTED");
    }

    @Test
    void firstPage_syncReady_projectionShortPage_fallsBackToLiveGmail() {
        UUID tenantId = seedTenant();
        markSyncReady(tenantId);
        seedProjectionRows(tenantId, 5); // < pageLimit of 20

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
                                        == RecentInboxUnavailableReason.NOT_CONNECTED,
                        "short projection page must NOT be returned as PROJECTION — orchestrator"
                                + " has to fall back, which then signals NOT_CONNECTED here");
    }

    private void markSyncReady(UUID tenantId) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                inboxSyncStateRepository.recordBackfillSuccess(
                                        tenantId, 999_999L, Instant.parse("2026-05-01T00:00:00Z")));
    }

    private void seedProjectionRows(UUID tenantId, int count) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            for (int rowIndex = 0; rowIndex < count; rowIndex++) {
                                inboxProjectionWriteService.upsert(
                                        new InboxProjectionUpsertCommand(
                                                tenantId,
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
}

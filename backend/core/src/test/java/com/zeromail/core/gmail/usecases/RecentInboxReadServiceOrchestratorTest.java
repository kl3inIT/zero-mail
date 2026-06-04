package com.zeromail.core.gmail.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxPage;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableException;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableReason;
import com.zeromail.core.inbox.domain.InboxProjectionDataSource;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateEntity;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository;
import com.zeromail.core.inbox.usecases.InboxBackfillEnqueuer;
import com.zeromail.core.inbox.usecases.InboxProjectionMessage;
import com.zeromail.core.inbox.usecases.InboxProjectionPage;
import com.zeromail.core.inbox.usecases.InboxProjectionReadService;
import com.zeromail.core.inbox.usecases.InvalidProjectionCursorException;
import com.zeromail.core.llm.gateway.sanitization.JsoupSafeHtmlSanitizer;
import com.zeromail.core.shared.crypto.CryptoProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Wave 1 orchestrator branch tests for {@link RecentInboxReadService#fetchPage}.
 *
 * <p>The cross-source integration test ({@code RecentInboxReadServiceFallbackTest} per Phase B
 * PLAN.md) lands in Wave 4 with real Postgres + Gmail mocks. These unit tests exercise the routing
 * logic in isolation by mocking the four collaborators directly:
 *
 * <ul>
 *   <li>{@link GmailInboxSyncStateRepository} — used for the freshness gate
 *   <li>{@link InboxProjectionReadService} — used for the projection path
 *   <li>{@link GmailConnectionRepository} — when the gmail fallback runs we let it throw {@code
 *       NOT_CONNECTED} as a sentinel, proving the fallback path was taken without needing a Gmail
 *       SDK mock
 *   <li>{@link InboxBackfillEnqueuer} — verified for the lazy-enqueue invariant on the SYNCING
 *       branch
 * </ul>
 */
class RecentInboxReadServiceOrchestratorTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private GmailConnectionRepository gmailConnectionRepository;
    private GmailApiClientFactory gmailApiClientFactory;
    private InboxBackfillEnqueuer inboxBackfillEnqueuer;
    private GmailInboxSyncStateRepository inboxSyncStateRepository;
    private InboxProjectionReadService inboxProjectionReadService;
    private RecentInboxReadService recentInboxReadService;

    @BeforeEach
    void setUp() {
        gmailConnectionRepository = mock(GmailConnectionRepository.class);
        gmailApiClientFactory = mock(GmailApiClientFactory.class);
        inboxBackfillEnqueuer = mock(InboxBackfillEnqueuer.class);
        inboxSyncStateRepository = mock(GmailInboxSyncStateRepository.class);
        inboxProjectionReadService = mock(InboxProjectionReadService.class);
        recentInboxReadService =
                new RecentInboxReadService(
                        gmailConnectionRepository,
                        gmailApiClientFactory,
                        cryptoProperties(),
                        new JsoupSafeHtmlSanitizer(),
                        inboxBackfillEnqueuer,
                        inboxSyncStateRepository,
                        inboxProjectionReadService);
    }

    @Test
    void firstPage_noSyncStateRow_servesLiveGmailAndEnqueuesBackfill() {
        when(inboxSyncStateRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(gmailConnectionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        // First connect no longer parks the user on an empty SYNCING banner; it serves the live
        // Gmail first page while the background backfill runs. With no GmailConnection row, the
        // live
        // path surfaces NOT_CONNECTED — reaching it proves the orchestrator took the live branch
        // instead of short-circuiting to SYNCING. The lazy backfill enqueue still fires.
        assertThatThrownBy(() -> recentInboxReadService.fetchPage(TENANT_ID, null, 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.NOT_CONNECTED);
        verify(inboxBackfillEnqueuer).enqueueIfNotPending(TENANT_ID);
        verify(gmailConnectionRepository).findByTenantId(TENANT_ID);
        verifyNoInteractions(inboxProjectionReadService);
    }

    @Test
    void firstPage_lastFullSyncAtNull_servesLiveGmail() {
        GmailInboxSyncStateEntity syncState = syncStateWithLastFullSyncAt(null);
        when(inboxSyncStateRepository.findById(TENANT_ID)).thenReturn(Optional.of(syncState));
        when(gmailConnectionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recentInboxReadService.fetchPage(TENANT_ID, null, 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.NOT_CONNECTED);
        verify(gmailConnectionRepository).findByTenantId(TENANT_ID);
        verifyNoInteractions(inboxProjectionReadService);
    }

    @Test
    void firstPage_syncReady_projectionFullPage_returnsProjectionWithPPrefix() {
        GmailInboxSyncStateEntity syncState =
                syncStateWithLastFullSyncAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(inboxSyncStateRepository.findById(TENANT_ID)).thenReturn(Optional.of(syncState));
        List<InboxProjectionMessage> rows = projectionRows(20);
        when(inboxProjectionReadService.fetchInboxPage(eq(TENANT_ID), eq(null), eq(20)))
                .thenReturn(
                        new InboxProjectionPage(
                                rows,
                                "inner-projection-cursor",
                                InboxProjectionDataSource.PROJECTION));

        RecentInboxPage page = recentInboxReadService.fetchPage(TENANT_ID, null, 20);

        assertThat(page.dataSource()).isEqualTo(InboxProjectionDataSource.PROJECTION);
        assertThat(page.messages()).hasSize(20);
        assertThat(page.nextCursor()).isEqualTo("Pinner-projection-cursor");
        verifyNoInteractions(gmailConnectionRepository, gmailApiClientFactory);
    }

    @Test
    void firstPage_syncReady_projectionShort_fallsBackToLiveGmail() {
        GmailInboxSyncStateEntity syncState =
                syncStateWithLastFullSyncAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(inboxSyncStateRepository.findById(TENANT_ID)).thenReturn(Optional.of(syncState));
        when(inboxProjectionReadService.fetchInboxPage(eq(TENANT_ID), eq(null), eq(20)))
                .thenReturn(
                        new InboxProjectionPage(
                                projectionRows(5), null, InboxProjectionDataSource.PROJECTION));
        when(gmailConnectionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recentInboxReadService.fetchPage(TENANT_ID, null, 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.NOT_CONNECTED,
                        "fallback should have invoked gmailForTenant which then signals NOT_CONNECTED");
        verify(gmailConnectionRepository).findByTenantId(TENANT_ID);
    }

    @Test
    void cursorWithPPrefix_routesToProjectionWithStrippedInnerCursor() {
        when(inboxProjectionReadService.fetchInboxPage(
                        eq(TENANT_ID), eq("inner-keyset-cursor"), anyInt()))
                .thenReturn(
                        new InboxProjectionPage(
                                projectionRows(3), null, InboxProjectionDataSource.PROJECTION));

        RecentInboxPage page =
                recentInboxReadService.fetchPage(TENANT_ID, "Pinner-keyset-cursor", 20);

        assertThat(page.dataSource()).isEqualTo(InboxProjectionDataSource.PROJECTION);
        assertThat(page.messages()).hasSize(3);
        assertThat(page.nextCursor()).isNull();
        ArgumentCaptor<String> innerCursorCaptor = ArgumentCaptor.forClass(String.class);
        verify(inboxProjectionReadService)
                .fetchInboxPage(eq(TENANT_ID), innerCursorCaptor.capture(), anyInt());
        assertThat(innerCursorCaptor.getValue()).isEqualTo("inner-keyset-cursor");
        // findById is called by the lazy backfill enqueue (Phase A invariant), but the orchestrator
        // routing on the cursor branch must not consult it again — projection routing is sticky.
        verifyNoInteractions(gmailConnectionRepository, gmailApiClientFactory);
    }

    @Test
    void cursorWithGPrefix_routesToLiveGmailPath() {
        // Empty inner cursor decodes to the legacy "first page" InboxCursor; the gmail connection
        // lookup then signals NOT_CONNECTED — proof that the G prefix routed past the projection
        // service and into the Gmail-bound code path.
        when(gmailConnectionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recentInboxReadService.fetchPage(TENANT_ID, "G", 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.NOT_CONNECTED,
                        "G prefix routes to live gmail; missing connection then signals NOT_CONNECTED");
        verifyNoInteractions(inboxProjectionReadService);
        verify(gmailConnectionRepository).findByTenantId(TENANT_ID);
    }

    @Test
    void cursorWithUnknownPrefix_throwsInvalidCursor() {
        assertThatThrownBy(
                        () ->
                                recentInboxReadService.fetchPage(
                                        TENANT_ID, "Xunknown-prefix-cursor", 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.INVALID_CURSOR);
        verifyNoInteractions(inboxProjectionReadService);
        verifyNoInteractions(gmailConnectionRepository, gmailApiClientFactory);
    }

    @Test
    void projectionInvalidCursor_isWrappedAsInvalidCursor() {
        when(inboxProjectionReadService.fetchInboxPage(eq(TENANT_ID), eq("bad-inner"), anyInt()))
                .thenThrow(new InvalidProjectionCursorException("Cursor HMAC signature mismatch"));

        assertThatThrownBy(() -> recentInboxReadService.fetchPage(TENANT_ID, "Pbad-inner", 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.INVALID_CURSOR);
    }

    private static GmailInboxSyncStateEntity syncStateWithLastFullSyncAt(Instant lastFullSyncAt) {
        GmailInboxSyncStateEntity syncState = mock(GmailInboxSyncStateEntity.class);
        when(syncState.getLastFullSyncAt()).thenReturn(lastFullSyncAt);
        return syncState;
    }

    private static List<InboxProjectionMessage> projectionRows(int count) {
        ArrayList<InboxProjectionMessage> rows = new ArrayList<>(count);
        for (int rowIndex = 0; rowIndex < count; rowIndex++) {
            rows.add(
                    new InboxProjectionMessage(
                            "msg-" + rowIndex,
                            "thread-" + rowIndex,
                            "Subject " + rowIndex,
                            "Snippet " + rowIndex,
                            "from-" + rowIndex + "@example.com",
                            List.of(),
                            List.of(),
                            Instant.parse("2026-05-01T12:00:00Z").minusSeconds(rowIndex),
                            List.of("INBOX"),
                            List.of(),
                            false,
                            false));
        }
        return List.copyOf(rows);
    }

    private static CryptoProperties cryptoProperties() {
        return new CryptoProperties(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }
}

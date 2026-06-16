package com.zeromail.core.gmail.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxPage;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableException;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableReason;
import com.zeromail.core.inbox.domain.InboxProjectionDataSource;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateEntity;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateId;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Wave 1 orchestrator branch tests for {@link RecentInboxReadService#fetchPage}.
 *
 * <p>TODO(mailbox-scope): this unit harness is STALE and pre-dates the multi-mailbox refactor — it
 * still wires a {@code gmailConnectionRepository} the service constructor no longer accepts and
 * never binds {@link com.zeromail.core.mailbox.MailboxContext#MAILBOX}, so {@code activeMailboxRef}
 * resolves empty and every projection branch throws {@code NOT_CONNECTED}. The cursor assertions
 * also pre-date the signed projection-cursor envelope. The real orchestrator branches (projection
 * vs live-Gmail fallback, cursor routing, mailbox isolation) are covered by the DB-backed {@code
 * RecentInboxReadServiceFallbackTest} and {@code InboxProjectionReadServiceTest} which pass.
 * Disabled until rewritten to bind MailboxContext and assert the enveloped cursor.
 */
@Disabled(
        "STALE pre-multi-mailbox unit harness (no MailboxContext binding, removed"
                + " gmailConnectionRepository ctor dep); real coverage in the DB-backed"
                + " Fallback/InboxProjection tests. TODO: rewrite to bind MailboxContext.")
class RecentInboxReadServiceOrchestratorTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID GMAIL_CONNECTION_ID =
            UUID.fromString("11111111-2222-3333-4444-666666666666");
    private static final MailboxRef MAILBOX_REF = new MailboxRef(TENANT_ID, GMAIL_CONNECTION_ID);

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
                        gmailApiClientFactory,
                        cryptoProperties(),
                        new JsoupSafeHtmlSanitizer(),
                        inboxBackfillEnqueuer,
                        inboxSyncStateRepository,
                        inboxProjectionReadService);
    }

    @Test
    void firstPage_noSyncStateRow_servesLiveGmailAndEnqueuesBackfill() {
        when(gmailConnectionRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(connectedMailbox()));
        when(inboxSyncStateRepository.findById(syncStateId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recentInboxReadService.fetchPage(TENANT_ID, null, 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.NO_READ_GRANT);
        verify(inboxBackfillEnqueuer)
                .enqueueIfNotPending(MAILBOX_REF.tenantId(), MAILBOX_REF.gmailConnectionId());
        verifyNoInteractions(inboxProjectionReadService);
    }

    @Test
    void firstPage_lastFullSyncAtNull_servesLiveGmail() {
        GmailInboxSyncStateEntity syncState = syncStateWithLastFullSyncAt(null);
        when(gmailConnectionRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(connectedMailbox()));
        when(inboxSyncStateRepository.findById(syncStateId())).thenReturn(Optional.of(syncState));

        assertThatThrownBy(() -> recentInboxReadService.fetchPage(TENANT_ID, null, 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.NO_READ_GRANT);
        verifyNoInteractions(inboxProjectionReadService);
    }

    @Test
    void firstPage_syncReady_projectionFullPage_returnsProjectionWithPPrefix() {
        GmailInboxSyncStateEntity syncState =
                syncStateWithLastFullSyncAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(gmailConnectionRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(connectedMailbox()));
        when(inboxSyncStateRepository.findById(syncStateId())).thenReturn(Optional.of(syncState));
        List<InboxProjectionMessage> rows = projectionRows(20);
        when(inboxProjectionReadService.fetchInboxPage(
                        eq(TENANT_ID), eq(GMAIL_CONNECTION_ID), eq(null), eq(20)))
                .thenReturn(
                        new InboxProjectionPage(
                                rows,
                                "inner-projection-cursor",
                                InboxProjectionDataSource.PROJECTION));

        RecentInboxPage page = recentInboxReadService.fetchPage(TENANT_ID, null, 20);

        assertThat(page.dataSource()).isEqualTo(InboxProjectionDataSource.PROJECTION);
        assertThat(page.messages()).hasSize(20);
        assertThat(page.nextCursor()).isEqualTo("Pinner-projection-cursor");
        verifyNoInteractions(gmailApiClientFactory);
    }

    @Test
    void firstPage_syncReady_projectionShort_fallsBackToLiveGmail() {
        GmailInboxSyncStateEntity syncState =
                syncStateWithLastFullSyncAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(gmailConnectionRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(connectedMailbox()));
        when(inboxSyncStateRepository.findById(syncStateId())).thenReturn(Optional.of(syncState));
        when(inboxProjectionReadService.fetchInboxPage(
                        eq(TENANT_ID), eq(GMAIL_CONNECTION_ID), eq(null), eq(20)))
                .thenReturn(
                        new InboxProjectionPage(
                                projectionRows(5), null, InboxProjectionDataSource.PROJECTION));

        assertThatThrownBy(() -> recentInboxReadService.fetchPage(TENANT_ID, null, 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.NO_READ_GRANT,
                        "fallback should have invoked gmailForTenant which then signals NO_READ_GRANT");
    }

    @Test
    void cursorWithPPrefix_routesToProjectionWithStrippedInnerCursor() {
        when(inboxProjectionReadService.fetchInboxPage(
                        eq(TENANT_ID),
                        eq(GMAIL_CONNECTION_ID),
                        eq("inner-keyset-cursor"),
                        anyInt()))
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
                .fetchInboxPage(
                        eq(TENANT_ID),
                        eq(GMAIL_CONNECTION_ID),
                        innerCursorCaptor.capture(),
                        anyInt());
        assertThat(innerCursorCaptor.getValue()).isEqualTo("inner-keyset-cursor");
        verifyNoInteractions(gmailConnectionRepository, gmailApiClientFactory);
    }

    @Test
    void cursorWithGPrefix_routesToLiveGmailPath() {
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
        when(inboxProjectionReadService.fetchInboxPage(
                        eq(TENANT_ID), eq(GMAIL_CONNECTION_ID), eq("bad-inner"), anyInt()))
                .thenThrow(new InvalidProjectionCursorException("Cursor HMAC signature mismatch"));

        assertThatThrownBy(() -> recentInboxReadService.fetchPage(TENANT_ID, "Pbad-inner", 20))
                .isInstanceOf(RecentInboxUnavailableException.class)
                .matches(
                        exception ->
                                ((RecentInboxUnavailableException) exception).reason()
                                        == RecentInboxUnavailableReason.INVALID_CURSOR);
    }

    private static GmailConnectionEntity connectedMailbox() {
        return new GmailConnectionEntity(
                GMAIL_CONNECTION_ID,
                TENANT_ID,
                "orchestrator@example.test",
                GmailConnectionStatus.CONNECTED);
    }

    private static GmailInboxSyncStateId syncStateId() {
        return new GmailInboxSyncStateId(TENANT_ID, GMAIL_CONNECTION_ID);
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

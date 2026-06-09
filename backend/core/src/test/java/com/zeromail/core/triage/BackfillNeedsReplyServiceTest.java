package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.gmail.usecases.RecentInboxReadService;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxMessage;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxPage;
import com.zeromail.core.inbox.domain.InboxProjectionDataSource;
import com.zeromail.core.triage.usecases.BackfillNeedsReplyService;
import com.zeromail.core.triage.usecases.TriageOrchestratorService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BackfillNeedsReplyServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000006b1");
    private static final UUID GMAIL_CONNECTION_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000006b2");

    @Test
    void backfill_caps_requested_limit_to_twenty_recent_threads() {
        RecentInboxReadService recentInboxReadService = mock(RecentInboxReadService.class);
        TriageOrchestratorService triageOrchestratorService = mock(TriageOrchestratorService.class);
        GmailConnectionService gmailConnectionService = mock(GmailConnectionService.class);
        BackfillNeedsReplyService backfillNeedsReplyService =
                new BackfillNeedsReplyService(
                        recentInboxReadService, triageOrchestratorService, gmailConnectionService);
        when(gmailConnectionService.primaryMailboxRef(TENANT_ID))
                .thenReturn(Optional.of(new MailboxRef(TENANT_ID, GMAIL_CONNECTION_ID)));
        when(recentInboxReadService.fetchPage(
                        eq(TENANT_ID), eq(null), eq(RecentInboxReadService.DEFAULT_PAGE_SIZE)))
                .thenReturn(
                        new RecentInboxPage(
                                messages(25), null, 25, 25, InboxProjectionDataSource.PROJECTION));

        BackfillNeedsReplyService.BackfillResult result =
                backfillNeedsReplyService.backfill(TENANT_ID, 100);

        assertThat(result.threadsScanned()).isEqualTo(20);
        assertThat(result.threadsClassified()).isEqualTo(20);
        assertThat(result.threadsFailed()).isZero();
        verify(triageOrchestratorService, times(20))
                .classifyReplyStatusForBackfill(any(MailMessageObserved.class));
    }

    private static List<RecentInboxMessage> messages(int count) {
        ArrayList<RecentInboxMessage> messages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            messages.add(
                    new RecentInboxMessage(
                            "gmail-message-" + index,
                            "gmail-thread-" + index,
                            "subject " + index,
                            "snippet " + index,
                            "sender@example.test",
                            List.of(),
                            List.of(),
                            Instant.parse("2026-06-07T00:00:00Z").plusSeconds(index),
                            List.of("INBOX"),
                            List.of(),
                            false,
                            false));
        }
        return List.copyOf(messages);
    }
}

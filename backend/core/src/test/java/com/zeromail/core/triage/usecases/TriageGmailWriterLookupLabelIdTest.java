package com.zeromail.core.triage.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.mailbox.MailboxRef;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * H-2 — {@code TriageGmailWriter.lookupLabelId(tenantId, labelName)} returns {@code
 * Optional<String>} so that {@code CampaignUndoService} can detect the "user manually deleted the
 * unsubscribed label" branch and skip the remove-label step without throwing.
 *
 * <p>Wave 0 RED: the {@code lookupLabelId(...)} method is not yet defined on {@link
 * TriageGmailWriter}; the reflective lookup throws {@link NoSuchMethodException}. Plan 06 Task 3
 * ships the implementation.
 */
class TriageGmailWriterLookupLabelIdTest {

    @Test
    void returnsLabelIdWhenLabelExists() throws Exception {
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Labels labels = mock(Gmail.Users.Labels.class);
        Gmail.Users.Labels.List listLabelsRequest = mock(Gmail.Users.Labels.List.class);
        GmailConnectionService gmailConnectionService = mock(GmailConnectionService.class);
        UUID tenantId = UUID.randomUUID();
        MailboxRef mailboxRef = new MailboxRef(tenantId, UUID.randomUUID());

        when(gmailConnectionService.primaryMailboxRef(tenantId))
                .thenReturn(Optional.of(mailboxRef));
        when(gmailApiClientFactory.buildClientForMailbox(mailboxRef)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.labels()).thenReturn(labels);
        when(labels.list("me")).thenReturn(listLabelsRequest);
        when(listLabelsRequest.execute())
                .thenReturn(
                        new ListLabelsResponse()
                                .setLabels(
                                        List.of(
                                                new Label()
                                                        .setId("Label_42")
                                                        .setName("Zero Mail/Unsubscribed"))));

        TriageGmailWriter triageGmailWriter =
                new TriageGmailWriter(
                        gmailApiClientFactory,
                        gmailConnectionService,
                        mock(com.zeromail.core.inbox.usecases.InboxProjectionWriteService.class));

        @SuppressWarnings("unchecked")
        Optional<String> labelId =
                (Optional<String>)
                        invokeLookupLabelId(triageGmailWriter, tenantId, "Zero Mail/Unsubscribed");

        assertThat(labelId).contains("Label_42");
    }

    @Test
    void returnsEmptyWhenLabelMissing() throws Exception {
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Labels labels = mock(Gmail.Users.Labels.class);
        Gmail.Users.Labels.List listLabelsRequest = mock(Gmail.Users.Labels.List.class);
        GmailConnectionService gmailConnectionService = mock(GmailConnectionService.class);
        UUID tenantId = UUID.randomUUID();
        MailboxRef mailboxRef = new MailboxRef(tenantId, UUID.randomUUID());

        when(gmailConnectionService.primaryMailboxRef(tenantId))
                .thenReturn(Optional.of(mailboxRef));
        when(gmailApiClientFactory.buildClientForMailbox(mailboxRef)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.labels()).thenReturn(labels);
        when(labels.list("me")).thenReturn(listLabelsRequest);
        when(listLabelsRequest.execute())
                .thenReturn(
                        new ListLabelsResponse()
                                .setLabels(
                                        List.of(
                                                new Label()
                                                        .setId("Label_99")
                                                        .setName("Some Other Label"))));

        TriageGmailWriter triageGmailWriter =
                new TriageGmailWriter(
                        gmailApiClientFactory,
                        gmailConnectionService,
                        mock(com.zeromail.core.inbox.usecases.InboxProjectionWriteService.class));

        @SuppressWarnings("unchecked")
        Optional<String> labelId =
                (Optional<String>)
                        invokeLookupLabelId(triageGmailWriter, tenantId, "Zero Mail/Unsubscribed");

        assertThat(labelId).isEqualTo(Optional.empty());
    }

    @Test
    void propagatesIoExceptionFromGmailApi() throws Exception {
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Labels labels = mock(Gmail.Users.Labels.class);
        Gmail.Users.Labels.List listLabelsRequest = mock(Gmail.Users.Labels.List.class);
        GmailConnectionService gmailConnectionService = mock(GmailConnectionService.class);
        UUID tenantId = UUID.randomUUID();
        MailboxRef mailboxRef = new MailboxRef(tenantId, UUID.randomUUID());

        when(gmailConnectionService.primaryMailboxRef(tenantId))
                .thenReturn(Optional.of(mailboxRef));
        when(gmailApiClientFactory.buildClientForMailbox(mailboxRef)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.labels()).thenReturn(labels);
        when(labels.list("me")).thenReturn(listLabelsRequest);
        when(listLabelsRequest.execute()).thenThrow(new IOException("simulated Gmail outage"));

        TriageGmailWriter triageGmailWriter =
                new TriageGmailWriter(
                        gmailApiClientFactory,
                        gmailConnectionService,
                        mock(com.zeromail.core.inbox.usecases.InboxProjectionWriteService.class));

        assertThatThrownBy(
                        () ->
                                invokeLookupLabelId(
                                        triageGmailWriter, tenantId, "Zero Mail/Unsubscribed"))
                .as("IOException must propagate (or wrap) to match applyLabel behavior")
                .isInstanceOf(Exception.class);
    }

    private static Object invokeLookupLabelId(
            TriageGmailWriter triageGmailWriter, UUID tenantId, String labelName) throws Exception {
        return TriageGmailWriter.class
                .getMethod("lookupLabelId", UUID.class, String.class)
                .invoke(triageGmailWriter, tenantId, labelName);
    }
}

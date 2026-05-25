package com.zeromail.core.chat.confirm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.SendCommitCommand;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.SendInFlightCommand;
import com.zeromail.core.chat.confirm.send.AssistantSendCommand;
import com.zeromail.core.chat.confirm.send.AssistantSendExecutor;
import com.zeromail.core.chat.confirm.send.GmailMessageBuilder;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.exception.VipAcknowledgmentMissingException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.outbound.usecases.GmailOutboundSendGateway;
import com.zeromail.core.shared.privacy.Sensitive;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.SenderEmailCanonicalizer;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SuppressWarnings({"unchecked", "rawtypes"})
class AssistantSendExecutorVipIT {

    private final GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
    private final ConfirmationStateMachine confirmationStateMachine =
            mock(ConfirmationStateMachine.class);
    private final ConfirmationLeaseService confirmationLeaseService =
            mock(ConfirmationLeaseService.class);
    private final SenderSafetyNetService senderSafetyNetService =
            mock(SenderSafetyNetService.class);
    private final SenderEmailCanonicalizer senderEmailCanonicalizer =
            mock(SenderEmailCanonicalizer.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private AssistantSendExecutor assistantSendExecutor;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(
                        invocation -> {
                            TransactionCallback<UUID> transactionCallback =
                                    invocation.getArgument(0);
                            return transactionCallback.doInTransaction(
                                    mock(TransactionStatus.class));
                        });
        doAnswer(
                        invocation -> {
                            java.util.function.Consumer<TransactionStatus> transactionCallback =
                                    invocation.getArgument(0);
                            transactionCallback.accept(mock(TransactionStatus.class));
                            return null;
                        })
                .when(transactionTemplate)
                .executeWithoutResult(any());
        when(senderEmailCanonicalizer.canonicalize(anyString()))
                .thenAnswer(
                        invocation ->
                                invocation
                                        .<String>getArgument(0)
                                        .trim()
                                        .toLowerCase(java.util.Locale.ROOT));
        when(senderEmailCanonicalizer.redisCacheKeyComponent(anyString()))
                .thenAnswer(invocation -> "hash-" + invocation.<String>getArgument(0));
        assistantSendExecutor =
                new AssistantSendExecutor(
                        new GmailOutboundSendGateway(gmailApiClientFactory),
                        new GmailMessageBuilder(),
                        confirmationStateMachine,
                        confirmationLeaseService,
                        senderSafetyNetService,
                        senderEmailCanonicalizer,
                        transactionTemplate,
                        new ObjectMapper());
    }

    @Test
    void vip_recipient_without_acknowledgment_is_rejected_before_audit_or_gmail_send()
            throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(senderSafetyNetService.isProtected(tenantId, "ceo@acme.test")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                withTenant(
                                        tenantId,
                                        () ->
                                                assistantSendExecutor.execute(
                                                        command(tenantId, false))))
                .isInstanceOf(VipAcknowledgmentMissingException.class);

        verify(confirmationStateMachine, never()).recordSendInFlight(any());
        verify(gmailApiClientFactory, never()).buildClientForTenant(any());
    }

    @Test
    void acknowledged_vip_recipient_sends_and_commits_audit_row() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        when(confirmationStateMachine.recordSendInFlight(any(SendInFlightCommand.class)))
                .thenReturn(auditId);
        Gmail gmail = configureGmail(tenantId);

        AssistantSendExecutor.AssistantSendResult result =
                withTenant(tenantId, () -> assistantSendExecutor.execute(command(tenantId, true)));

        assertThat(result.state()).isEqualTo("CONFIRMED");
        verify(gmail).users();
        verify(confirmationStateMachine).recordSendInFlight(any(SendInFlightCommand.class));
        verify(confirmationStateMachine)
                .commitSendCompleted(eq(auditId), any(SendCommitCommand.class));
    }

    /**
     * CR-01 regression: multi-recipient sends must include every recipient in the audit
     * fingerprint. Earlier code used findFirst() and recorded only the lexicographically smallest
     * canonical recipient. A send to a VIP plus a filler could be hidden by listing the filler
     * first. We compute the hash on the joined sorted canonical list, and a single recipient
     * produces a different hash than the same recipient mixed with other addresses.
     */
    @Test
    void recipient_hash_covers_all_to_cc_and_bcc_recipients() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        when(confirmationStateMachine.recordSendInFlight(any(SendInFlightCommand.class)))
                .thenReturn(auditId);
        configureGmail(tenantId);

        ArgumentCaptor<SendInFlightCommand> sendInFlightCaptor =
                ArgumentCaptor.forClass(SendInFlightCommand.class);

        AssistantSendCommand singleRecipientCommand =
                new AssistantSendCommand(
                        tenantId,
                        UUID.randomUUID(),
                        "tool-send-single",
                        ChatToolName.SEND_EMAIL,
                        "filler@acme.test",
                        null,
                        null,
                        "Board note",
                        Sensitive.of("body"),
                        null,
                        null,
                        null,
                        true,
                        Map.of("state", "preview"),
                        "confirm-test-single");
        withTenant(tenantId, () -> assistantSendExecutor.execute(singleRecipientCommand));

        AssistantSendCommand multiRecipientCommand =
                new AssistantSendCommand(
                        tenantId,
                        UUID.randomUUID(),
                        "tool-send-multi",
                        ChatToolName.SEND_EMAIL,
                        "filler@acme.test, ceo@acme.test",
                        "watcher@acme.test",
                        "audit@acme.test",
                        "Board note",
                        Sensitive.of("body"),
                        null,
                        null,
                        null,
                        true,
                        Map.of("state", "preview"),
                        "confirm-test-multi");
        withTenant(tenantId, () -> assistantSendExecutor.execute(multiRecipientCommand));

        verify(confirmationStateMachine, org.mockito.Mockito.times(2))
                .recordSendInFlight(sendInFlightCaptor.capture());
        java.util.List<SendInFlightCommand> capturedSends = sendInFlightCaptor.getAllValues();
        String singleHash = capturedSends.get(0).recipientHash();
        String multiHash = capturedSends.get(1).recipientHash();
        assertThat(singleHash).isNotEqualTo(multiHash);
    }

    private Gmail configureGmail(UUID tenantId) throws Exception {
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Messages messages = mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.Send sendRequest = mock(Gmail.Users.Messages.Send.class);
        when(gmailApiClientFactory.buildClientForTenant(tenantId)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.send(eq("me"), any(Message.class))).thenReturn(sendRequest);
        when(sendRequest.execute())
                .thenReturn(new Message().setId("gmail-sent-1").setThreadId("thread-1"));
        return gmail;
    }

    private static AssistantSendCommand command(UUID tenantId, boolean vipAcknowledged) {
        return new AssistantSendCommand(
                tenantId,
                UUID.randomUUID(),
                "tool-send-vip",
                ChatToolName.SEND_EMAIL,
                "ceo@acme.test",
                null,
                null,
                "Board note",
                Sensitive.of("Please review the attached board note."),
                null,
                null,
                null,
                vipAcknowledged,
                Map.of("state", "preview"),
                "confirm-test-vip");
    }

    private static <T> T withTenant(UUID tenantId, TenantCallable<T> tenantCallable)
            throws Exception {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantCallable::call);
    }

    @FunctionalInterface
    private interface TenantCallable<T> {
        T call() throws Exception;
    }
}

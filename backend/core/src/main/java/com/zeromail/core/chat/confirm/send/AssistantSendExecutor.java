package com.zeromail.core.chat.confirm.send;

import com.zeromail.core.chat.confirm.ConfirmationLeaseService;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.SendCommitCommand;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.SendInFlightCommand;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.exception.GmailSendFailedException;
import com.zeromail.core.chat.exception.VipAcknowledgmentMissingException;
import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.outbound.usecases.ForwardMessageAssembler;
import com.zeromail.core.outbound.usecases.OutboundSendCommand;
import com.zeromail.core.outbound.usecases.OutboundSendException;
import com.zeromail.core.outbound.usecases.OutboundSendGateway;
import com.zeromail.core.outbound.usecases.OutboundSendResult;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.SenderEmailCanonicalizer;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AssistantSendExecutor {

    private final OutboundSendGateway outboundSendGateway;
    private final GmailMessageBuilder gmailMessageBuilder;
    private final ForwardMessageAssembler forwardMessageAssembler;
    private final GmailConnectionService gmailConnectionService;
    private final ConfirmationStateMachine confirmationStateMachine;
    private final ConfirmationLeaseService confirmationLeaseService;
    private final SenderSafetyNetService senderSafetyNetService;
    private final SenderEmailCanonicalizer senderEmailCanonicalizer;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public AssistantSendExecutor(
            OutboundSendGateway outboundSendGateway,
            GmailMessageBuilder gmailMessageBuilder,
            ForwardMessageAssembler forwardMessageAssembler,
            GmailConnectionService gmailConnectionService,
            ConfirmationStateMachine confirmationStateMachine,
            ConfirmationLeaseService confirmationLeaseService,
            SenderSafetyNetService senderSafetyNetService,
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper) {
        this.outboundSendGateway = outboundSendGateway;
        this.gmailMessageBuilder = gmailMessageBuilder;
        this.forwardMessageAssembler = forwardMessageAssembler;
        this.gmailConnectionService = gmailConnectionService;
        this.confirmationStateMachine = confirmationStateMachine;
        this.confirmationLeaseService = confirmationLeaseService;
        this.senderSafetyNetService = senderSafetyNetService;
        this.senderEmailCanonicalizer = senderEmailCanonicalizer;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    public AssistantSendResult execute(AssistantSendCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String boundTenantId = TenantContext.currentOrThrow();
        if (!boundTenantId.equals(command.tenantId().toString())) {
            throw new IllegalStateException("tenant context does not match send command");
        }
        try {
            rejectUnacknowledgedVipRecipient(command);

            String preGeneratedMessageId =
                    gmailMessageBuilder.generateMessageId(
                            command.tenantId().toString(), command.chatId(), command.toolCallId());
            MailboxRef mailboxRef = primaryMailboxRef(command.tenantId());
            com.google.api.services.gmail.model.Message gmailMessage =
                    buildOutboundMessage(command, mailboxRef, preGeneratedMessageId);
            UUID auditId =
                    transactionTemplate.execute(
                            _ ->
                                    confirmationStateMachine.recordSendInFlight(
                                            new SendInFlightCommand(
                                                    command.tenantId(),
                                                    command.chatId(),
                                                    command.toolCallId(),
                                                    command.toolName(),
                                                    preGeneratedMessageId,
                                                    recipientHash(command),
                                                    subjectHash(command.subject()),
                                                    writeJson(
                                                            Map.of(
                                                                    "state",
                                                                    "send_in_flight",
                                                                    "tool",
                                                                    command.toolName().id())),
                                                    writeJson(command.previewSnapshot()))));
            OutboundSendResult sendResult;
            try {
                sendResult =
                        outboundSendGateway.send(
                                new OutboundSendCommand(
                                        command.tenantId(), mailboxRef, gmailMessage));
            } catch (IOException | OutboundSendException gmailSendFailure) {
                transactionTemplate.executeWithoutResult(
                        _ ->
                                confirmationStateMachine.commitSendFailed(
                                        auditId,
                                        command.toolCallId(),
                                        gmailSendFailure.getClass().getSimpleName()));
                throw new GmailSendFailedException(gmailSendFailure);
            }
            Map<String, Object> resultSummary = new LinkedHashMap<>();
            resultSummary.put("state", "committed");
            if (sendResult != null && hasText(sendResult.gmailMessageId())) {
                resultSummary.put("gmail_api_message_id", sendResult.gmailMessageId());
            }
            if (sendResult != null && hasText(sendResult.gmailThreadId())) {
                resultSummary.put("gmail_thread_id", sendResult.gmailThreadId());
            }
            // commitSendCompleted runs the audit transition AND publishes
            // AssistantSendCompleted inside the same @Transactional method. Idempotent: if the
            // row was already moved to COMMITTED by the reconciliation cron, it returns false
            // without throwing and without re-publishing the event (CR-02 / WR-10).
            transactionTemplate.executeWithoutResult(
                    _ ->
                            confirmationStateMachine.commitSendCompleted(
                                    auditId,
                                    new SendCommitCommand(
                                            command.tenantId(),
                                            command.chatId(),
                                            command.toolCallId(),
                                            writeJson(resultSummary))));
            return new AssistantSendResult("CONFIRMED", auditId, resultSummary);
        } finally {
            confirmationLeaseService.release(
                    command.chatId(), command.toolCallId(), command.processInstanceId());
        }
    }

    private com.google.api.services.gmail.model.Message buildOutboundMessage(
            AssistantSendCommand command, MailboxRef mailboxRef, String preGeneratedMessageId) {
        if (command.toolName() == ChatToolName.FORWARD_EMAIL) {
            // A real forward must carry the original message, not just the user's note. The chat
            // forward path previously built a plain new email whose body was the note only,
            // dropping
            // the forwarded content entirely. Re-attach the source message as message/rfc822.
            try {
                return forwardMessageAssembler.buildForward(
                        mailboxRef,
                        command.sourceMessageId(),
                        java.util.List.of(command.to()),
                        hasText(command.cc())
                                ? java.util.List.of(command.cc())
                                : java.util.List.of(),
                        command.subject(),
                        command.body().value(),
                        preGeneratedMessageId);
            } catch (IOException forwardBuildFailure) {
                throw new GmailSendFailedException(forwardBuildFailure);
            }
        }
        return gmailMessageBuilder.build(command, preGeneratedMessageId);
    }

    private MailboxRef primaryMailboxRef(UUID tenantId) {
        // TODO(Plan 05): replace primary-shim MailboxRef with MailboxContext.currentOrThrow()
        // once the active-mailbox filter binds chat actions to the selected mailbox.
        return gmailConnectionService
                .primaryMailboxRef(tenantId)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Primary Gmail mailbox is required for assistant send"));
    }

    private void rejectUnacknowledgedVipRecipient(AssistantSendCommand command) {
        if (command.vipAcknowledged()) {
            return;
        }
        for (String recipient : recipients(command)) {
            if (senderSafetyNetService.isProtected(command.tenantId(), recipient)) {
                throw new VipAcknowledgmentMissingException();
            }
        }
    }

    private static java.util.List<String> recipients(AssistantSendCommand command) {
        java.util.ArrayList<String> recipients = new java.util.ArrayList<>();
        appendRecipients(recipients, command.to());
        appendRecipients(recipients, command.cc());
        appendRecipients(recipients, command.bcc());
        return java.util.List.copyOf(recipients);
    }

    private static void appendRecipients(java.util.List<String> recipients, String rawRecipients) {
        if (!hasText(rawRecipients)) {
            return;
        }
        try {
            for (InternetAddress internetAddress : InternetAddress.parse(rawRecipients, false)) {
                if (hasText(internetAddress.getAddress())) {
                    recipients.add(internetAddress.getAddress());
                }
            }
        } catch (AddressException addressException) {
            throw new IllegalArgumentException("recipient address is invalid", addressException);
        }
    }

    private String recipientHash(AssistantSendCommand command) {
        // Hash the joined canonicalized recipient list so the audit fingerprint covers ALL
        // recipients (to + cc + bcc), not just the lexicographically smallest one (CR-01).
        // The previous findFirst() implementation let a multi-recipient send to a VIP + filler
        // record only the filler's hash, defeating the safety-net audit contract.
        java.util.List<String> canonicalRecipients =
                recipients(command).stream()
                        .map(senderEmailCanonicalizer::canonicalize)
                        .sorted()
                        .collect(Collectors.toUnmodifiableList());
        if (canonicalRecipients.isEmpty()) {
            return hexSha256("missing-recipient");
        }
        String joinedCanonicalRecipients = String.join(",", canonicalRecipients);
        return hexSha256(joinedCanonicalRecipients);
    }

    private static String subjectHash(String subject) {
        return hexSha256(subject == null ? "" : subject);
    }

    private static String hexSha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(
                            messageDigest.digest(
                                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException(
                    "SHA-256 digest is unavailable", noSuchAlgorithmException);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException("send audit JSON is invalid", jacksonException);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AssistantSendResult(
            String state, UUID auditId, Map<String, Object> resultSummary) {}
}

package com.zeromail.core.chat.confirm.send;

import com.google.api.services.gmail.Gmail;
import com.zeromail.core.chat.confirm.ConfirmationLeaseService;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.SendCommitCommand;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.SendInFlightCommand;
import com.zeromail.core.chat.domain.event.AssistantSendCompleted;
import com.zeromail.core.chat.exception.GmailSendFailedException;
import com.zeromail.core.chat.exception.VipAcknowledgmentMissingException;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.SenderEmailCanonicalizer;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@AllowedSendCallSite
public class AssistantSendExecutor {

    private final GmailApiClientFactory gmailApiClientFactory;
    private final GmailMessageBuilder gmailMessageBuilder;
    private final ConfirmationStateMachine confirmationStateMachine;
    private final ConfirmationLeaseService confirmationLeaseService;
    private final SenderSafetyNetService senderSafetyNetService;
    private final SenderEmailCanonicalizer senderEmailCanonicalizer;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public AssistantSendExecutor(
            GmailApiClientFactory gmailApiClientFactory,
            GmailMessageBuilder gmailMessageBuilder,
            ConfirmationStateMachine confirmationStateMachine,
            ConfirmationLeaseService confirmationLeaseService,
            SenderSafetyNetService senderSafetyNetService,
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            TransactionTemplate transactionTemplate,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper) {
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.gmailMessageBuilder = gmailMessageBuilder;
        this.confirmationStateMachine = confirmationStateMachine;
        this.confirmationLeaseService = confirmationLeaseService;
        this.senderSafetyNetService = senderSafetyNetService;
        this.senderEmailCanonicalizer = senderEmailCanonicalizer;
        this.transactionTemplate = transactionTemplate;
        this.applicationEventPublisher = applicationEventPublisher;
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
            com.google.api.services.gmail.model.Message gmailMessage =
                    gmailMessageBuilder.build(command, preGeneratedMessageId);
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
            com.google.api.services.gmail.model.Message sendResult;
            try {
                Gmail gmail = gmailApiClientFactory.buildClientForTenant(command.tenantId());
                sendResult = gmail.users().messages().send("me", gmailMessage).execute();
            } catch (IOException | InvalidGrantException | IllegalStateException gmailSendFailure) {
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
            if (sendResult != null && hasText(sendResult.getId())) {
                resultSummary.put("gmail_api_message_id", sendResult.getId());
            }
            if (sendResult != null && hasText(sendResult.getThreadId())) {
                resultSummary.put("gmail_thread_id", sendResult.getThreadId());
            }
            transactionTemplate.executeWithoutResult(
                    _ ->
                            confirmationStateMachine.commitSendCompleted(
                                    auditId,
                                    new SendCommitCommand(
                                            command.tenantId(),
                                            command.chatId(),
                                            command.toolCallId(),
                                            writeJson(resultSummary))));
            Instant sentAt = Instant.now();
            applicationEventPublisher.publishEvent(
                    new AssistantSendCompleted(
                            command.tenantId().toString(),
                            command.chatId(),
                            command.toolCallId(),
                            auditId,
                            sentAt));
            return new AssistantSendResult("CONFIRMED", auditId, resultSummary);
        } finally {
            confirmationLeaseService.release(command.chatId(), command.toolCallId());
        }
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
        return recipients(command).stream()
                .map(senderEmailCanonicalizer::canonicalize)
                .sorted()
                .map(senderEmailCanonicalizer::redisCacheKeyComponent)
                .findFirst()
                .orElseGet(() -> hexSha256("missing-recipient"));
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

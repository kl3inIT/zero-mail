package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.confirm.ConfirmationStateMachine.Reservation;
import com.zeromail.core.chat.confirm.send.AssistantSendCommand;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.exception.RecipientInvalidException;
import com.zeromail.core.shared.privacy.Sensitive;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConfirmedSendToolHandlers {

    private final SenderSafetyNetService senderSafetyNetService;

    public ConfirmedSendToolHandlers(SenderSafetyNetService senderSafetyNetService) {
        this.senderSafetyNetService = senderSafetyNetService;
    }

    public AssistantSendCommand toCommand(
            Reservation reservation,
            boolean vipAcknowledged,
            Map<String, Object> contentOverride,
            String processInstanceId) {
        if (reservation.toolCategory()
                != com.zeromail.core.chat.domain.ToolCategory.CONFIRMED_SEND) {
            throw new IllegalArgumentException("tool is not a confirmed-send tool");
        }
        Map<String, Object> effectiveInput = new LinkedHashMap<>(reservation.inputJson());
        if (contentOverride != null) {
            effectiveInput.putAll(contentOverride);
        }
        String recipient = WriteToolArguments.text(effectiveInput, "to");
        // Validate recipient addresses synchronously here (inside toCommand) so an invalid
        // address — e.g. the assistant put a display name like "Nhat Nhu" in `to` instead of an
        // email — reverts the reservation back to PENDING (ConfirmActionService catches this and
        // calls revertReservation) and surfaces a dedicated 400 the user can act on, rather than
        // failing later in AssistantSendExecutor where the row would be stuck in PROCESSING.
        requireEmailRecipients(recipient);
        requireEmailRecipients(WriteToolArguments.optionalText(effectiveInput, "cc"));
        requireEmailRecipients(WriteToolArguments.optionalText(effectiveInput, "bcc"));
        boolean safetyNetRecipient =
                senderSafetyNetService.isProtected(reservation.tenantId(), recipient);
        Map<String, Object> previewSnapshot = new LinkedHashMap<>(reservation.confirmationJson());
        previewSnapshot.put("vip", safetyNetRecipient);
        previewSnapshot.put("vipAcknowledged", vipAcknowledged);
        previewSnapshot.putIfAbsent("input", Map.copyOf(effectiveInput));
        return new AssistantSendCommand(
                reservation.tenantId(),
                reservation.chatId(),
                reservation.toolCallId(),
                reservation.toolName(),
                recipient,
                WriteToolArguments.optionalText(effectiveInput, "cc"),
                WriteToolArguments.optionalText(effectiveInput, "bcc"),
                subjectOrEmpty(effectiveInput),
                Sensitive.of(body(reservation.toolName(), effectiveInput)),
                WriteToolArguments.optionalText(effectiveInput, "sourceMessageId"),
                WriteToolArguments.optionalText(effectiveInput, "gmailThreadId"),
                WriteToolArguments.optionalText(effectiveInput, "inReplyToMessageId"),
                vipAcknowledged,
                Map.copyOf(previewSnapshot),
                processInstanceId);
    }

    /**
     * Subject is blank-tolerant: Gmail renders an empty subject as "(no subject)". The assistant is
     * instructed to always compose one, but a user who clears the subject field on the preview card
     * (contentOverride) must still be able to send rather than hit a validation failure that
     * reverts the action to PENDING.
     */
    /**
     * Reject a recipient field that is not a parseable email address. {@code null}/blank is allowed
     * (optional cc/bcc); a non-blank value must parse into at least one address and every parsed
     * address must contain a real local@domain shape. Mirrors the parse {@link
     * com.zeromail.core.chat.confirm.send.AssistantSendExecutor} performs before send so validation
     * fails early (revertible) rather than mid-send (stuck PROCESSING).
     */
    private static void requireEmailRecipients(String rawRecipients) {
        if (rawRecipients == null || rawRecipients.isBlank()) {
            return;
        }
        try {
            InternetAddress[] parsedAddresses = InternetAddress.parse(rawRecipients, false);
            if (parsedAddresses.length == 0) {
                throw new RecipientInvalidException();
            }
            for (InternetAddress parsedAddress : parsedAddresses) {
                String emailAddress = parsedAddress.getAddress();
                int atIndex = emailAddress == null ? -1 : emailAddress.indexOf('@');
                if (atIndex <= 0 || atIndex >= emailAddress.length() - 1) {
                    throw new RecipientInvalidException();
                }
            }
        } catch (AddressException addressException) {
            throw new RecipientInvalidException(addressException);
        }
    }

    private static String subjectOrEmpty(Map<String, Object> effectiveInput) {
        String subject = WriteToolArguments.optionalText(effectiveInput, "subject");
        return subject == null ? "" : subject;
    }

    private static String body(ChatToolName toolName, Map<String, Object> effectiveInput) {
        if (toolName == ChatToolName.FORWARD_EMAIL) {
            String additionalBody =
                    WriteToolArguments.optionalText(effectiveInput, "additionalBody");
            if (additionalBody != null) {
                return additionalBody;
            }
        }
        return WriteToolArguments.text(effectiveInput, "body");
    }
}

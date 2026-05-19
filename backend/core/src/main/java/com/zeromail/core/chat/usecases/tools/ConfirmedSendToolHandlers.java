package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.confirm.ConfirmationStateMachine.Reservation;
import com.zeromail.core.chat.confirm.send.AssistantSendCommand;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.shared.privacy.Sensitive;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
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
                WriteToolArguments.text(effectiveInput, "subject"),
                Sensitive.of(body(reservation.toolName(), effectiveInput)),
                WriteToolArguments.optionalText(effectiveInput, "sourceMessageId"),
                WriteToolArguments.optionalText(effectiveInput, "gmailThreadId"),
                WriteToolArguments.optionalText(effectiveInput, "inReplyToMessageId"),
                vipAcknowledged,
                Map.copyOf(previewSnapshot),
                processInstanceId);
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

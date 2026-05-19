package com.zeromail.core.chat.confirm.send;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.shared.privacy.Sensitive;
import java.util.Map;
import java.util.UUID;

public record AssistantSendCommand(
        UUID tenantId,
        UUID chatId,
        String toolCallId,
        ChatToolName toolName,
        String to,
        String cc,
        String bcc,
        String subject,
        Sensitive<String> body,
        String sourceMessageId,
        String gmailThreadId,
        String inReplyToMessageId,
        boolean vipAcknowledged,
        Map<String, Object> previewSnapshot) {}

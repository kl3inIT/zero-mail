package com.zeromail.core.chat.confirm.send;

import com.zeromail.core.chat.domain.ChatToolName;
import java.util.Map;
import java.util.UUID;

public record AssistantWriteCommand(
        UUID tenantId,
        UUID chatId,
        String toolCallId,
        ChatToolName toolName,
        Map<String, Object> inputJson,
        Map<String, Object> previewSnapshot) {}

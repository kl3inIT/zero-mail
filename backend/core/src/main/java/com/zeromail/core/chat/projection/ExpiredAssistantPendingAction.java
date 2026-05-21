package com.zeromail.core.chat.projection;

import java.util.UUID;

/** Row shape returned by the assistant pending-action reconciliation sweep. */
public record ExpiredAssistantPendingAction(
        UUID pendingActionId, UUID tenantId, UUID chatId, String toolCallId) {}

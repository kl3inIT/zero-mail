package com.zeromail.core.chat.projection;

import java.util.UUID;

/**
 * Row shape returned by the assistant SEND_IN_FLIGHT reconciliation sweep. Carries the Gmail
 * message id so the reconciler can probe Gmail to decide CONFIRMED vs FAILED.
 */
public record StaleAssistantSendAudit(
        UUID auditId, UUID tenantId, UUID chatId, String toolCallId, String gmailMessageId) {}

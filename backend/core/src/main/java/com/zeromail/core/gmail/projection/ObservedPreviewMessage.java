package com.zeromail.core.gmail.projection;

import java.time.Instant;

/**
 * Row projection from {@code mail_message_observed} used as the seed for Gmail preview reads
 * (recent inbox samples and triage input fetches). Carries only the locally observed metadata; the
 * full message details come from the Gmail API.
 */
public record ObservedPreviewMessage(
        String gmailMessageId,
        String gmailThreadId,
        String[] labelIds,
        Long internalDate,
        Instant observedAt) {}

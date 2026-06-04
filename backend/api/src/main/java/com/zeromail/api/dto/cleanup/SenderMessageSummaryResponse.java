package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.projection.SenderMessageSummary;
import java.time.Instant;

public record SenderMessageSummaryResponse(
        String gmailMessageId,
        String gmailThreadId,
        String subject,
        String snippet,
        Instant internalDate,
        boolean archived,
        boolean unread) {

    public static SenderMessageSummaryResponse from(SenderMessageSummary summary) {
        return new SenderMessageSummaryResponse(
                summary.gmailMessageId(),
                summary.gmailThreadId(),
                summary.subject(),
                summary.snippet(),
                summary.internalDate(),
                summary.archived(),
                summary.unread());
    }
}

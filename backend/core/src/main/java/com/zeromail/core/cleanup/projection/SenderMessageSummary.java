package com.zeromail.core.cleanup.projection;

import java.time.Instant;
import java.util.Objects;

/**
 * One email-row metadata for the Stats dialog email list (UNS-stats-02). Fetched live from Gmail
 * with {@code q=from:<senderEmail>} so the listing reflects the user's <i>current</i> Gmail state
 * — not a DB snapshot which could be stale after archive/trash.
 *
 * <p>{@code archived} = the message is not in INBOX label (covers archived + already moved to
 * other label by the user). {@code unread} = UNREAD label is present.
 */
public record SenderMessageSummary(
        String gmailMessageId,
        String gmailThreadId,
        String subject,
        String snippet,
        Instant internalDate,
        boolean archived,
        boolean unread) {

    public SenderMessageSummary {
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
        Objects.requireNonNull(internalDate, "internalDate must not be null");
        subject = Objects.requireNonNullElse(subject, "");
        snippet = Objects.requireNonNullElse(snippet, "");
    }
}

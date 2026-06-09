package com.zeromail.core.gmail.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event consumed by core.triage when Gmail ingestion observes a new inbox message.
 * Carries only stable Gmail ids and a timestamp; never subject, snippet, body, or sender display
 * name.
 */
public record MailMessageObserved(
        UUID tenantId,
        UUID gmailConnectionId,
        String gmailMessageId,
        String gmailThreadId,
        Instant observedAt) {

    /**
     * @deprecated transitional null-mailbox seam; Plan 03 migrates all callers to the
     *     mailbox-carrying constructor and removes this overload.
     */
    @Deprecated(forRemoval = true)
    public MailMessageObserved(
            UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt) {
        this(tenantId, null, gmailMessageId, gmailThreadId, observedAt);
    }
}

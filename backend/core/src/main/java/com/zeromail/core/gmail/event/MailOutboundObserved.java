package com.zeromail.core.gmail.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Integration event consumed when Gmail ingestion observes an outbound message. Carries only stable
 * Gmail ids and a timestamp; never subject, snippet, body, or sender display name.
 */
public record MailOutboundObserved(
        UUID tenantId,
        UUID gmailConnectionId,
        String gmailThreadId,
        String gmailMessageId,
        Instant observedAt) {

    public MailOutboundObserved {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        gmailMessageId = requireText(gmailMessageId, "gmailMessageId");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }

    /**
     * @deprecated transitional null-mailbox seam; Plan 03 migrates all callers to the
     *     mailbox-carrying constructor and removes this overload.
     */
    @Deprecated(forRemoval = true)
    public MailOutboundObserved(
            UUID tenantId, String gmailThreadId, String gmailMessageId, Instant observedAt) {
        this(tenantId, null, gmailThreadId, gmailMessageId, observedAt);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}

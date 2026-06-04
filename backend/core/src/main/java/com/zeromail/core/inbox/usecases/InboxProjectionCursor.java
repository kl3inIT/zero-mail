package com.zeromail.core.inbox.usecases;

import java.time.Instant;
import java.util.Objects;

/**
 * Decoded keyset cursor for the inbox projection page query. {@code receivedAt = null} and {@code
 * gmailMessageId = null} means "first page" — the read service short-circuits the keyset predicate
 * and returns the newest rows by index order.
 */
public record InboxProjectionCursor(Instant receivedAt, String gmailMessageId) {

    public InboxProjectionCursor {
        if ((receivedAt == null) != (gmailMessageId == null)) {
            throw new IllegalArgumentException(
                    "InboxProjectionCursor receivedAt and gmailMessageId must both be null or both"
                            + " non-null");
        }
        if (gmailMessageId != null && gmailMessageId.isBlank()) {
            throw new IllegalArgumentException("gmailMessageId must not be blank when set");
        }
    }

    public static InboxProjectionCursor firstPage() {
        return new InboxProjectionCursor(null, null);
    }

    public boolean isFirstPage() {
        return receivedAt == null;
    }

    /**
     * Convenience for constructing a cursor pointing at the supplied row; both arguments are
     * required (use {@link #firstPage()} otherwise).
     */
    public static InboxProjectionCursor of(Instant receivedAt, String gmailMessageId) {
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        return new InboxProjectionCursor(receivedAt, gmailMessageId);
    }
}

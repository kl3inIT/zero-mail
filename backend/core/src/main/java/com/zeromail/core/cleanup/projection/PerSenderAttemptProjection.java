package com.zeromail.core.cleanup.projection;

import com.zeromail.core.cleanup.domain.UnsubscribeAttemptState;
import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import java.time.Instant;
import java.util.Objects;

/**
 * Read-side projection of one {@code unsubscribe_attempt} row inside a campaign. Surfaced by the
 * campaign status endpoint (UNS-05) so the UI can render per-sender result rows.
 *
 * <p>{@code failureReason} is non-null iff {@link UnsubscribeAttemptState#FAILED} — controllers
 * translate it to a user-facing message.
 *
 * <p>{@code archivedMessageCount} reflects how many history messages were archived for this sender
 * as part of the campaign; zero is valid (e.g. the sender had no archivable history when the worker
 * reached it).
 */
public record PerSenderAttemptProjection(
        String senderEmail,
        String senderDomain,
        UnsubscribeMethod unsubscribeMethod,
        UnsubscribeAttemptState state,
        String failureReason,
        int archivedMessageCount,
        Instant startedAt,
        Instant finishedAt) {

    public PerSenderAttemptProjection {
        Objects.requireNonNull(senderEmail, "senderEmail must not be null");
        Objects.requireNonNull(unsubscribeMethod, "unsubscribeMethod must not be null");
        Objects.requireNonNull(state, "state must not be null");
        if (archivedMessageCount < 0) {
            throw new IllegalArgumentException(
                    "archivedMessageCount must be >= 0, was " + archivedMessageCount);
        }
    }
}

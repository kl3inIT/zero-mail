package com.zeromail.core.cleanup.projection;

import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import java.time.Instant;
import java.util.Objects;

/**
 * Read-side projection of one candidate sender that has at least one observed message with a {@code
 * List-Unsubscribe} header (URL or mailto, but not yet suppressed) inside the requested window.
 * Powers {@code GET /api/unsubscribe/candidates} (UNS-01).
 *
 * <p>Aggregation contract — one row per distinct {@code senderEmail} grouped from {@code
 * mail_message_observed}:
 *
 * <ul>
 *   <li>{@code messageCount}: total observed messages from the sender in the window.
 *   <li>{@code lastSeenAt}: maximum {@code observed_at} from the group.
 *   <li>{@code unsubscribeMethod}: {@link UnsubscribeMethod#ONE_CLICK} if any message in the group
 *       has {@code list_unsubscribe_one_click = true}; else {@link UnsubscribeMethod#MAILTO} if any
 *       message has {@code list_unsubscribe_mailto IS NOT NULL}; else {@link
 *       UnsubscribeMethod#NONE}. The query SHOULD filter {@code NONE} out before this row
 *       materializes — the field stays for forward-compat with a future "candidates without a
 *       List-Unsubscribe header" view.
 *   <li>{@code suppressed}: always {@code false} for the candidate query (the SQL anti-join
 *       excludes suppressed senders). Field kept for future enrichment queries that surface
 *       suppressed senders for admin tooling.
 * </ul>
 */
public record UnsubscribeCandidateProjection(
        String senderEmail,
        String senderDomain,
        long messageCount,
        Instant lastSeenAt,
        UnsubscribeMethod unsubscribeMethod,
        boolean suppressed) {

    public UnsubscribeCandidateProjection {
        Objects.requireNonNull(senderEmail, "senderEmail must not be null");
        Objects.requireNonNull(senderDomain, "senderDomain must not be null");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
        Objects.requireNonNull(unsubscribeMethod, "unsubscribeMethod must not be null");
    }
}

package com.zeromail.core.cleanup.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Static policy constants for the bulk-unsubscribe campaign pipeline (Phase 8).
 *
 * <p>All values are locked by product decision (D-04 / D-09 / D-20 / D-23 in {@code
 * 08-CONTEXT.md}); changes require a new planning phase. Constants live in one place so
 * controllers, services, and worker share a single source of truth.
 */
public final class UnsubscribeCampaignPolicy {

    /**
     * Maximum number of distinct sender emails a single campaign can target (UNS-03a). Enforced by
     * {@code CampaignCreateService}; violation raises {@link
     * com.zeromail.core.cleanup.exception.CampaignCapExceededException} with {@code
     * kind=TOO_MANY_SENDERS}.
     */
    public static final int MAX_SENDERS_PER_CAMPAIGN = 25;

    /** Maximum senders the Inbox Zero-style table can load for browsing/searching. */
    public static final int MAX_CANDIDATE_SENDERS = 500;

    /**
     * Maximum number of history messages the campaign will archive across all targeted senders
     * (UNS-03b). Worker stops archiving once this threshold is hit even if some senders still have
     * older messages. Violation raises {@link
     * com.zeromail.core.cleanup.exception.CampaignCapExceededException} with {@code
     * kind=TOO_MANY_MESSAGES}.
     */
    public static final int MAX_HISTORY_MESSAGES_PER_CAMPAIGN = 2000;

    /**
     * Sliding window after a campaign {@code applied_at} during which the user can issue a single
     * one-shot undo (UNS-07). Past the window, {@link
     * com.zeromail.core.cleanup.exception.UndoWindowExpiredException} is raised and the only path
     * to restore archived messages is per-message manual action in Gmail.
     */
    public static final Duration UNDO_WINDOW = Duration.ofDays(30);

    /**
     * Gmail label applied to every history message archived as part of a campaign. Lets the user
     * (and the undo path) locate "what cleanup touched" without scanning every audit row. Worker
     * lazily creates this label via {@code TriageGmailWriter.ensureLabelExists} on the first
     * archive of each campaign.
     */
    public static final String UNSUBSCRIBED_LABEL_NAME = "Zero Mail/Unsubscribed";

    /** Short throttle bucket window — at most 1 unsubscribe attempt per domain per 60s (D-20). */
    public static final Duration THROTTLE_PER_DOMAIN_WINDOW_60S = Duration.ofSeconds(60);

    /** Long throttle bucket window — at most 10 unsubscribe attempts per domain per hour (D-20). */
    public static final Duration THROTTLE_PER_DOMAIN_WINDOW_1H = Duration.ofHours(1);

    /** Short-bucket cap (D-20). */
    public static final int THROTTLE_MAX_PER_DOMAIN_60S = 1;

    /** Long-bucket cap (D-20). */
    public static final int THROTTLE_MAX_PER_DOMAIN_1H = 10;

    private UnsubscribeCampaignPolicy() {}

    /**
     * Compute the timestamp at which a campaign that was applied at {@code appliedAt} ceases to be
     * undoable. Pure function — no clock, no tenant context.
     */
    public static Instant undoableUntil(Instant appliedAt) {
        return Objects.requireNonNull(appliedAt, "appliedAt must not be null").plus(UNDO_WINDOW);
    }
}

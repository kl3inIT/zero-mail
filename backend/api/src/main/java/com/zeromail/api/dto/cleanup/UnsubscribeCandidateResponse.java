package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import java.time.Instant;

/**
 * One candidate sender surfaced by {@code GET /api/unsubscribe/candidates} (UNS-01).
 *
 * <p>{@code unsubscribeMethod} is the {@link com.zeromail.core.cleanup.domain.UnsubscribeMethod} id
 * string ({@code ONE_CLICK} / {@code MAILTO} / {@code NONE}). {@code suppressed} is always {@code
 * false} for the candidate list — the SQL anti-join in {@code CandidateQueryService} already
 * excludes suppressed senders; the field is kept on the wire for forward-compat with a future
 * admin-side view that surfaces suppressed senders.
 */
public record UnsubscribeCandidateResponse(
        String senderEmail,
        String senderDomain,
        long messageCount,
        Instant lastSeenAt,
        String unsubscribeMethod,
        boolean suppressed) {

    public static UnsubscribeCandidateResponse from(UnsubscribeCandidateProjection projection) {
        return new UnsubscribeCandidateResponse(
                projection.senderEmail(),
                projection.senderDomain(),
                projection.messageCount(),
                projection.lastSeenAt(),
                projection.unsubscribeMethod().id(),
                projection.suppressed());
    }
}

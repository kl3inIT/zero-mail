package com.zeromail.api.dto.cleanup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import java.time.Instant;

/**
 * One candidate sender surfaced by {@code GET /api/unsubscribe/candidates} (UNS-01).
 *
 * <p>{@code senderName} is the best-effort display name extracted from the From header (e.g. "John
 * Doe" from {@code "John Doe" <john@x>}). Frontend falls back to a local-part formatter when this
 * is {@code null}.
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
        @JsonInclude(JsonInclude.Include.NON_NULL) String senderName,
        long messageCount,
        long readMessageCount,
        Instant lastSeenAt,
        String unsubscribeMethod,
        @JsonInclude(JsonInclude.Include.NON_NULL) String status,
        boolean suppressed) {

    public static UnsubscribeCandidateResponse from(UnsubscribeCandidateProjection projection) {
        return new UnsubscribeCandidateResponse(
                projection.senderEmail(),
                projection.senderDomain(),
                projection.senderName(),
                projection.messageCount(),
                projection.readMessageCount(),
                projection.lastSeenAt(),
                projection.unsubscribeMethod().id(),
                projection.status(),
                projection.suppressed());
    }
}

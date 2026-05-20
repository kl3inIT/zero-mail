package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.projection.PerSenderAttemptProjection;
import java.time.Instant;

/**
 * Per-sender row inside a {@link CampaignStatusResponse} (UNS-05). {@code state} is the {@link
 * com.zeromail.core.cleanup.domain.UnsubscribeAttemptState} id string ({@code PENDING} / {@code
 * RUNNING} / {@code OK} / {@code FAILED}). {@code failureReason} is non-null iff {@code
 * state=FAILED}.
 */
public record PerSenderStateResponse(
        String senderEmail,
        String senderDomain,
        String unsubscribeMethod,
        String state,
        String failureReason,
        int archivedMessageCount,
        Instant startedAt,
        Instant finishedAt) {

    public static PerSenderStateResponse from(PerSenderAttemptProjection projection) {
        return new PerSenderStateResponse(
                projection.senderEmail(),
                projection.senderDomain(),
                projection.unsubscribeMethod().id(),
                projection.state().id(),
                projection.failureReason(),
                projection.archivedMessageCount(),
                projection.startedAt(),
                projection.finishedAt());
    }
}

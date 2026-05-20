package com.zeromail.core.cleanup.projection;

import com.zeromail.core.cleanup.domain.CampaignStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-side projection of one {@code unsubscribe_campaign} row plus its per-sender attempts.
 * Surfaced by {@code GET /api/unsubscribe/campaigns/{id}} (UNS-05) and the campaign list endpoint
 * (UNS-06).
 *
 * <p>{@code appliedAt} and {@code revertedAt} carry undo state (UNS-07): both null while the
 * campaign is RUNNING, {@code appliedAt} populated on COMPLETED, {@code revertedAt} populated after
 * a successful undo.
 *
 * <p>{@code perSender} is defensively copied to {@link List#copyOf(java.util.Collection)} —
 * controllers cannot mutate the projection after construction.
 */
public record CampaignStatusProjection(
        UUID id,
        UUID jobId,
        CampaignStatus status,
        Instant appliedAt,
        Instant revertedAt,
        int totalSenderCount,
        int totalHistoryMessageCount,
        Instant createdAt,
        List<PerSenderAttemptProjection> perSender) {

    public CampaignStatusProjection {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (totalSenderCount < 0) {
            throw new IllegalArgumentException(
                    "totalSenderCount must be >= 0, was " + totalSenderCount);
        }
        if (totalHistoryMessageCount < 0) {
            throw new IllegalArgumentException(
                    "totalHistoryMessageCount must be >= 0, was " + totalHistoryMessageCount);
        }
        perSender = List.copyOf(Objects.requireNonNull(perSender, "perSender must not be null"));
    }
}

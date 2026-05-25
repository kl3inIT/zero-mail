package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.projection.CampaignStatusProjection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Polling response for {@code GET /api/unsubscribe/campaigns/{jobId}} (UNS-05).
 *
 * <p>{@code progressPct} is computed in the controller as {@code (terminal / total) * 100} where
 * terminal = {@code OK + FAILED}. {@code undoAvailable} is the boolean the frontend gates the
 * "Undo" button on — true iff {@code status=COMPLETED}, {@code appliedAt != null}, {@code
 * revertedAt == null}, and {@code now < appliedAt + 30d}.
 */
public record CampaignStatusResponse(
        UUID campaignId,
        UUID jobId,
        String status,
        int progressPct,
        Instant appliedAt,
        Instant revertedAt,
        int totalSenderCount,
        int totalHistoryMessageCount,
        List<PerSenderStateResponse> perSender,
        boolean undoAvailable) {

    public CampaignStatusResponse {
        perSender = List.copyOf(perSender);
    }

    public static CampaignStatusResponse from(
            CampaignStatusProjection projection, boolean undoAvailable, int progressPct) {
        return new CampaignStatusResponse(
                projection.id(),
                projection.jobId(),
                projection.status().id(),
                progressPct,
                projection.appliedAt(),
                projection.revertedAt(),
                projection.totalSenderCount(),
                projection.totalHistoryMessageCount(),
                projection.perSender().stream().map(PerSenderStateResponse::from).toList(),
                undoAvailable);
    }
}

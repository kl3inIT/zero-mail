package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.usecases.CampaignPreviewService;
import java.util.List;

/**
 * Wrapper response for {@code POST /api/unsubscribe/campaigns/preview} (UNS-03).
 *
 * <p>{@code totalHistoryCount} mirrors the service's archivable history count — only senders that
 * {@code willArchive=true} contribute. The frontend uses this aggregate to render the "X messages
 * will be archived" confirmation copy.
 */
public record CampaignPreviewResponse(
        List<PerSenderPreviewResponse> perSender, long totalHistoryCount) {

    public CampaignPreviewResponse {
        perSender = List.copyOf(perSender);
    }

    public static CampaignPreviewResponse from(
            CampaignPreviewService.CampaignPreviewResult result) {
        return new CampaignPreviewResponse(
                result.perSender().stream().map(PerSenderPreviewResponse::from).toList(),
                result.archivableHistoryCount());
    }
}

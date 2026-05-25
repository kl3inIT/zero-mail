package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.usecases.CampaignPreviewService;

/**
 * Per-sender row inside a {@link CampaignPreviewResponse} (UNS-03). {@code riskBadge} is the stable
 * UI key the frontend localizes ({@code SAFE} / {@code NO_HEADER_DISABLED} / {@code
 * SUPPRESSED_BLOCKED}). {@code willArchive} is the single source of truth for whether the worker
 * will include the sender if the user confirms the campaign.
 */
public record PerSenderPreviewResponse(
        String senderEmail,
        String senderDomain,
        String unsubscribeMethod,
        long historyMessageCount,
        boolean willArchive,
        String riskBadge) {

    public static PerSenderPreviewResponse from(CampaignPreviewService.PerSenderPreview row) {
        return new PerSenderPreviewResponse(
                row.senderEmail(),
                row.senderDomain(),
                row.unsubscribeMethod().id(),
                row.messageCount(),
                row.willArchive(),
                row.riskBadge());
    }
}

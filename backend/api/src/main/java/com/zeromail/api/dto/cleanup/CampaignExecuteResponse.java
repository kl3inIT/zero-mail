package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.usecases.CampaignExecuteService;
import java.util.UUID;

/**
 * Wrapper response for {@code POST /api/unsubscribe/campaigns/execute} (UNS-04).
 *
 * <p>{@code jobId} is the polling key the frontend uses to fetch campaign status (UNS-05). {@code
 * status} is hard-wired to {@code QUEUED} — the worker picks up the {@code processing_job} row
 * asynchronously after this transaction commits.
 */
public record CampaignExecuteResponse(UUID campaignId, UUID jobId, String status) {

    public static CampaignExecuteResponse from(
            CampaignExecuteService.CampaignExecuteResult result) {
        return new CampaignExecuteResponse(result.campaignId(), result.jobId(), "QUEUED");
    }
}

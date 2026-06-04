package com.zeromail.api.dto.thread;

import com.zeromail.core.thread.projection.NeedsReplyCounts;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"toReplyCount", "awaitingCount", "draftedCount"})
public record NeedsReplyCountsResponse(long toReplyCount, long awaitingCount, long draftedCount) {

    public static NeedsReplyCountsResponse from(NeedsReplyCounts counts) {
        return new NeedsReplyCountsResponse(counts.toReply(), counts.awaiting(), counts.drafted());
    }
}

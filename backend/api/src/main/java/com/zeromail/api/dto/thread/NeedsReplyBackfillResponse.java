package com.zeromail.api.dto.thread;

import com.zeromail.core.triage.usecases.BackfillNeedsReplyService.BackfillResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"threadsScanned", "threadsClassified", "threadsFailed"})
public record NeedsReplyBackfillResponse(
        int threadsScanned, int threadsClassified, int threadsFailed) {

    public static NeedsReplyBackfillResponse from(BackfillResult result) {
        return new NeedsReplyBackfillResponse(
                result.threadsScanned(), result.threadsClassified(), result.threadsFailed());
    }
}

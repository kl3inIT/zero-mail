package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.usecases.CleanupSenderActionService.BulkSenderActionResult;

public record CleanupSenderActionResponse(
        int senderCount, int affectedMessageCount, int failedMessageCount) {

    public static CleanupSenderActionResponse from(BulkSenderActionResult result) {
        return new CleanupSenderActionResponse(
                result.senderCount(), result.affectedMessageCount(), result.failedMessageCount());
    }
}

package com.zeromail.api.dto.thread;

import com.zeromail.core.draft.usecases.GenerateThreadDraftResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"draftId", "gmailThreadId", "status", "openInGmailUrl"})
public record ThreadDraftResponse(
        String draftId, String gmailThreadId, String status, String openInGmailUrl) {

    public static ThreadDraftResponse from(GenerateThreadDraftResult result) {
        return new ThreadDraftResponse(
                result.draftId(),
                result.gmailThreadId(),
                result.status().name(),
                result.openInGmailUrl());
    }
}

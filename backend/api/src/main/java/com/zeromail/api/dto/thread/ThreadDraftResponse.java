package com.zeromail.api.dto.thread;

import com.zeromail.core.draft.usecases.GenerateThreadDraftResult;

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

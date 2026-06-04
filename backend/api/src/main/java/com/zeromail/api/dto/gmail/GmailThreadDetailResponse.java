package com.zeromail.api.dto.gmail;

import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxThreadDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A full Gmail conversation for the inbox reader: every message in the thread (received plus the
 * tenant's own sent replies), oldest-first. Bodies are rendered live and never persisted.
 */
@Schema(requiredProperties = {"gmailThreadId", "subject", "messages"})
public record GmailThreadDetailResponse(
        String gmailThreadId, String subject, List<GmailInboxMessageDetailResponse> messages) {

    public static GmailThreadDetailResponse from(RecentInboxThreadDetail detail) {
        return new GmailThreadDetailResponse(
                detail.gmailThreadId(),
                detail.subject(),
                detail.messages().stream().map(GmailInboxMessageDetailResponse::from).toList());
    }
}

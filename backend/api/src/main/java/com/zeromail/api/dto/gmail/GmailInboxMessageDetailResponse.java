package com.zeromail.api.dto.gmail;

import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxMessageDetail;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"message", "renderedText", "renderedHtml"})
public record GmailInboxMessageDetailResponse(
        GmailInboxMessageResponse message,
        @Schema(
                        description =
                                "Transient Gmail text/plain render fallback. Not persisted by Zero Mail.")
                String renderedText,
        @Schema(description = "Transient sanitized Gmail HTML render. Not persisted by Zero Mail.")
                String renderedHtml) {

    public static GmailInboxMessageDetailResponse from(RecentInboxMessageDetail detail) {
        return new GmailInboxMessageDetailResponse(
                GmailInboxMessageResponse.from(detail.message()),
                detail.renderedText(),
                detail.renderedHtml());
    }
}

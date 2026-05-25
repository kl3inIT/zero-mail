package com.zeromail.api.dto.gmail;

import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"items", "nextCursor", "loadedCount", "maxMessages"})
public record GmailInboxPageResponse(
        List<GmailInboxMessageResponse> items,
        @Schema(nullable = true) String nextCursor,
        int loadedCount,
        int maxMessages) {

    public GmailInboxPageResponse {
        items = List.copyOf(items);
    }

    public static GmailInboxPageResponse from(RecentInboxPage page) {
        return new GmailInboxPageResponse(
                page.messages().stream().map(GmailInboxMessageResponse::from).toList(),
                page.nextCursor(),
                page.loadedCount(),
                page.maxMessages());
    }
}

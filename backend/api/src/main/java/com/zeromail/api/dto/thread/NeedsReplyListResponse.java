package com.zeromail.api.dto.thread;

import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailThreadDisplay;
import com.zeromail.core.thread.projection.NeedsReplyPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(requiredProperties = {"items", "nextCursor", "toReplyCount"})
public record NeedsReplyListResponse(
        List<NeedsReplyRowResponse> items,
        @Schema(nullable = true) String nextCursor,
        @Schema(nullable = true) Long toReplyCount) {

    public NeedsReplyListResponse {
        items = List.copyOf(items);
    }

    public static NeedsReplyListResponse from(
            NeedsReplyPage page,
            Map<String, GmailThreadDisplay> displaysByThreadId,
            Long toReplyCount) {
        return new NeedsReplyListResponse(
                page.items().stream()
                        .map(
                                row ->
                                        NeedsReplyRowResponse.from(
                                                row, displaysByThreadId.get(row.gmailThreadId())))
                        .toList(),
                page.nextCursor(),
                toReplyCount);
    }
}

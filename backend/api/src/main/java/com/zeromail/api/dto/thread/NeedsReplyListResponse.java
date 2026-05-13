package com.zeromail.api.dto.thread;

import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailThreadDisplay;
import com.zeromail.core.thread.projection.NeedsReplyPage;
import java.util.List;
import java.util.Map;

public record NeedsReplyListResponse(
        List<NeedsReplyRowResponse> items, String nextCursor, Long toReplyCount) {

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

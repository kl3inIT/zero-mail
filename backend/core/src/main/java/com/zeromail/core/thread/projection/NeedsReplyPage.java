package com.zeromail.core.thread.projection;

import java.util.List;
import java.util.Objects;

public record NeedsReplyPage(List<NeedsReplyRow> items, String nextCursor) {

    public NeedsReplyPage {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }
}

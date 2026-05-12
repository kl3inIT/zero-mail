package com.zeromail.core.thread.projection;

import com.zeromail.core.thread.domain.ThreadReplyBucket;
import java.util.Objects;

public record NeedsReplyPageQuery(
        ThreadReplyBucket bucket, boolean resolvedOnly, int limit, String cursor) {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    public NeedsReplyPageQuery {
        if (!resolvedOnly) {
            Objects.requireNonNull(bucket, "bucket must not be null when resolvedOnly is false");
        }
        limit = clampLimit(limit);
        cursor = cursor == null || cursor.isBlank() ? null : cursor.trim();
    }

    private static int clampLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }
}

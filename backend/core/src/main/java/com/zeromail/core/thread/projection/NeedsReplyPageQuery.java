package com.zeromail.core.thread.projection;

import com.zeromail.core.thread.domain.ThreadReplyBucket;
import java.util.Objects;

public record NeedsReplyPageQuery(
        ThreadReplyBucket bucket,
        boolean resolvedOnly,
        boolean draftedOnly,
        int limit,
        String cursor) {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 20;

    public NeedsReplyPageQuery(
            ThreadReplyBucket bucket, boolean resolvedOnly, int limit, String cursor) {
        this(bucket, resolvedOnly, false, limit, cursor);
    }

    public NeedsReplyPageQuery {
        if (!resolvedOnly) {
            Objects.requireNonNull(bucket, "bucket must not be null when resolvedOnly is false");
        }
        if (draftedOnly && resolvedOnly) {
            throw new IllegalArgumentException("draftedOnly is not supported for resolved queries");
        }
        if (draftedOnly && bucket != ThreadReplyBucket.TO_REPLY) {
            throw new IllegalArgumentException("draftedOnly requires the TO_REPLY bucket");
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

package com.zeromail.core.thread.projection;

import java.time.Instant;
import java.util.Objects;

public record NeedsReplyRow(
        String gmailThreadId,
        String bucket,
        boolean hasDraft,
        String draftId,
        Instant lastClassifiedAt,
        boolean resolved) {

    public NeedsReplyRow {
        Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
        Objects.requireNonNull(bucket, "bucket must not be null");
    }
}

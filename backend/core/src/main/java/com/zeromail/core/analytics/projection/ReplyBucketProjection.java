package com.zeromail.core.analytics.projection;

public record ReplyBucketProjection(String bucket, long count, long withDraft) {}

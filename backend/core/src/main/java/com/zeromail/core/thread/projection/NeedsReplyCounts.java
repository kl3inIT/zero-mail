package com.zeromail.core.thread.projection;

/**
 * Unresolved thread counts per needs-reply tab. {@code drafted} is the subset of {@code toReply}
 * that already has a Zero Mail draft (the synthetic "Đã sinh nháp" tab is a client-side filter over
 * the to-reply bucket, so its count is to-reply threads with {@code has_draft = true}).
 */
public record NeedsReplyCounts(long toReply, long awaiting, long drafted) {}

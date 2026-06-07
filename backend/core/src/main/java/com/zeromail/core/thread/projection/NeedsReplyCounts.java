package com.zeromail.core.thread.projection;

/**
 * Unresolved thread counts per needs-reply tab. {@code drafted} is the subset of {@code toReply}
 * that already has a Zero Mail draft, exposed as a public tab while staying stored as {@code
 * TO_REPLY + has_draft = true}.
 */
public record NeedsReplyCounts(long toReply, long awaiting, long drafted) {}

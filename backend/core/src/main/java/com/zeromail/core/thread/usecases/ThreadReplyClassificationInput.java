package com.zeromail.core.thread.usecases;

import java.util.Objects;
import java.util.UUID;

/**
 * Inputs the deterministic reply-status classifier maps to a {@link
 * com.zeromail.core.thread.domain.ThreadReplyBucket}.
 *
 * <p>{@code inboundReplyNeeded} is the needs-reply-vs-FYI signal for an inbound message ({@code
 * lastMessageFromIsTenant=false}). The thread module cannot reach the LLM gateway, so the caller
 * (the triage orchestrator) resolves this flag — by an LLM classification, a cheap no-reply
 * pre-filter, or "a reply was already drafted" — and passes the decision in. It is ignored when the
 * last message is from the tenant (that path is the deterministic {@code AWAITING_THEIR_REPLY}
 * bucket) or when the message is an auto-reply (always FYI).
 */
public record ThreadReplyClassificationInput(
        UUID tenantId,
        String gmailThreadId,
        String lastMessageId,
        boolean lastMessageFromIsTenant,
        boolean threadHasSentLabel,
        boolean hasZeroMailDraft,
        String zeroMailDraftId,
        boolean lastMessageIsAutoReply,
        boolean inboundReplyNeeded) {

    public ThreadReplyClassificationInput {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        lastMessageId = requireText(lastMessageId, "lastMessageId");
        zeroMailDraftId = nullIfBlank(zeroMailDraftId);
        if (hasZeroMailDraft && zeroMailDraftId == null) {
            throw new IllegalArgumentException("zeroMailDraftId must not be blank");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.zeromail.core.inbox.usecases;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Command DTO for {@link InboxProjectionWriteService#upsert(InboxProjectionUpsertCommand)}.
 *
 * <p>Plaintext metadata fields are nullable because the Pub/Sub observed listener intentionally
 * keeps the initial projection minimal (no extra Gmail metadata header fetch) and lets the
 * backfill service in Wave 3 enrich the row later. {@code senderEmail} is mandatory because it
 * feeds the deterministic HMAC column ({@code sender_email_hash}); without it the row would lose
 * its future lookup ability.
 */
public record InboxProjectionUpsertCommand(
        UUID tenantId,
        String gmailMessageId,
        String gmailThreadId,
        String senderEmail,
        String senderDisplayName,
        String subject,
        String snippet,
        boolean hasAttachment,
        Instant receivedAt,
        List<String> labelIds,
        long sourceHistoryId) {

    public InboxProjectionUpsertCommand {
        java.util.Objects.requireNonNull(tenantId, "tenantId must not be null");
        java.util.Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        java.util.Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
        java.util.Objects.requireNonNull(senderEmail, "senderEmail must not be null");
        java.util.Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        java.util.Objects.requireNonNull(labelIds, "labelIds must not be null");
        labelIds = List.copyOf(labelIds);
    }
}

package com.zeromail.core.gmail.projection;

import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import java.time.Instant;
import java.util.UUID;

/** Metadata-only mailbox summary for account management surfaces; excludes OAuth ciphertext. */
public record MailboxSummaryProjection(
        UUID gmailConnectionId,
        String googleEmail,
        String displayPurpose,
        String status,
        boolean isPrimary,
        Instant watchExpiresAt,
        String ingestionHealth,
        Long lastSyncedHistoryId,
        Instant connectedAt) {

    public static MailboxSummaryProjection from(GmailConnectionEntity gmailConnection) {
        return new MailboxSummaryProjection(
                gmailConnection.getId(),
                gmailConnection.getGoogleEmail(),
                gmailConnection.getDisplayPurpose(),
                gmailConnection.getStatus().name(),
                gmailConnection.isPrimary(),
                gmailConnection.getWatchExpiresAt(),
                gmailConnection.getIngestionHealth().name(),
                gmailConnection.getLastSyncedHistoryId(),
                gmailConnection.getConnectedAt());
    }
}

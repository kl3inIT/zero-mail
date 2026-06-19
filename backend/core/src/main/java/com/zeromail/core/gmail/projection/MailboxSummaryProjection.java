package com.zeromail.core.gmail.projection;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import java.time.Instant;
import java.util.UUID;

/** Metadata-only mailbox summary for account management surfaces; excludes OAuth ciphertext. */
public record MailboxSummaryProjection(
        UUID gmailConnectionId,
        String googleEmail,
        String displayPurpose,
        String profileDisplayName,
        String profilePictureUrl,
        String status,
        boolean isPrimary,
        Instant watchExpiresAt,
        String ingestionHealth,
        Long lastSyncedHistoryId,
        Instant connectedAt) {

    public static MailboxSummaryProjection from(GmailConnectionEntity gmailConnection) {
        // Internal sync/watch pointers are only meaningful for a CONNECTED mailbox. For a
        // DISCONNECTED row the watch is stopped and the history pointer is a stale internal
        // detail, so null them out rather than leaking them through the account-management API.
        boolean isConnected = gmailConnection.getStatus() == GmailConnectionStatus.CONNECTED;
        return new MailboxSummaryProjection(
                gmailConnection.getId(),
                gmailConnection.getGoogleEmail(),
                gmailConnection.getDisplayPurpose(),
                gmailConnection.getGoogleProfileName(),
                gmailConnection.getGoogleProfilePictureUrl(),
                gmailConnection.getStatus().name(),
                gmailConnection.isPrimary(),
                isConnected ? gmailConnection.getWatchExpiresAt() : null,
                gmailConnection.getIngestionHealth().name(),
                isConnected ? gmailConnection.getLastSyncedHistoryId() : null,
                gmailConnection.getConnectedAt());
    }
}

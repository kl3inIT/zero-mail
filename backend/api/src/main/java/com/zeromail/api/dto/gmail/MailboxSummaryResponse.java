package com.zeromail.api.dto.gmail;

import com.zeromail.core.gmail.projection.MailboxSummaryProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "gmailConnectionId",
            "googleEmail",
            "status",
            "isPrimary",
            "ingestionHealth"
        })
public record MailboxSummaryResponse(
        UUID gmailConnectionId,
        String googleEmail,
        @Schema(nullable = true) String displayPurpose,
        @Schema(nullable = true) String profileDisplayName,
        @Schema(nullable = true) String profilePictureUrl,
        String status,
        boolean isPrimary,
        @Schema(nullable = true) Instant watchExpiresAt,
        String ingestionHealth,
        @Schema(nullable = true) Long lastSyncedHistoryId,
        @Schema(nullable = true) Instant connectedAt) {

    public static MailboxSummaryResponse from(MailboxSummaryProjection projection) {
        return new MailboxSummaryResponse(
                projection.gmailConnectionId(),
                projection.googleEmail(),
                projection.displayPurpose(),
                projection.profileDisplayName(),
                projection.profilePictureUrl(),
                projection.status(),
                projection.isPrimary(),
                projection.watchExpiresAt(),
                projection.ingestionHealth(),
                projection.lastSyncedHistoryId(),
                projection.connectedAt());
    }
}

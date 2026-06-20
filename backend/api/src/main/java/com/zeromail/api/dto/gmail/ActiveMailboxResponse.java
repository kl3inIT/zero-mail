package com.zeromail.api.dto.gmail;

import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"gmailConnectionId", "email", "status", "isPrimary"})
public record ActiveMailboxResponse(
        UUID gmailConnectionId,
        String email,
        @Schema(nullable = true) String displayPurpose,
        @Schema(nullable = true) String profileDisplayName,
        @Schema(nullable = true) String profilePictureUrl,
        String status,
        boolean isPrimary) {

    public static ActiveMailboxResponse from(GmailConnectionEntity gmailConnection) {
        return new ActiveMailboxResponse(
                gmailConnection.getId(),
                gmailConnection.getGoogleEmail(),
                gmailConnection.getDisplayPurpose(),
                gmailConnection.getGoogleProfileName(),
                gmailConnection.getGoogleProfilePictureUrl(),
                gmailConnection.getStatus().name(),
                gmailConnection.isPrimary());
    }
}

package com.zeromail.api.dto.account;

import com.zeromail.core.account.projection.CurrentUserProjection;
import com.zeromail.core.gmail.projection.GmailConnectionProjection;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        requiredProperties = {
            "userId",
            "tenantId",
            "email",
            "onboardingStep",
            "preferredLanguage",
            "triagePaused",
            "gmailConnectionStatus"
        })
public record MeResponse(
        String userId,
        String tenantId,
        String email,
        String onboardingStep,
        String preferredLanguage,
        boolean triagePaused,
        GmailConnectionStatusExtended gmailConnectionStatus,
        @Schema(
                        nullable = true,
                        description =
                                "Google profile display name. Read transiently from the OAuth"
                                        + " session principal on each request — never persisted to the"
                                        + " database (privacy).")
                String displayName,
        @Schema(
                        nullable = true,
                        description =
                                "Google profile picture URL. Read transiently from the OAuth"
                                        + " session principal on each request — never persisted to the"
                                        + " database (privacy).")
                String avatarUrl) {

    @Schema(requiredProperties = {"status", "ingestionHealth", "googleEmail"})
    public record GmailConnectionStatusExtended(
            String status, String ingestionHealth, @Schema(nullable = true) String googleEmail) {}

    public static MeResponse from(
            CurrentUserProjection user,
            boolean triagePaused,
            GmailConnectionStatusExtended gmailStatus,
            String displayName,
            String avatarUrl) {
        return new MeResponse(
                user.userId().toString(),
                user.tenantId().toString(),
                user.email(),
                user.onboardingStep(),
                user.preferredLanguage(),
                triagePaused,
                gmailStatus,
                displayName,
                avatarUrl);
    }

    public static MeResponse from(
            CurrentUserProjection user,
            boolean triagePaused,
            GmailConnectionProjection gmailConnection,
            String displayName,
            String avatarUrl) {
        return from(
                user,
                triagePaused,
                new GmailConnectionStatusExtended(
                        gmailConnection.status(),
                        gmailConnection.ingestionHealth(),
                        gmailConnection.googleEmail()),
                displayName,
                avatarUrl);
    }
}

package com.zeromail.api.dto.account;

import com.zeromail.core.account.projection.CurrentUserProjection;
import com.zeromail.core.gmail.projection.GmailConnectionProjection;

public record MeResponse(
        String userId,
        String tenantId,
        String email,
        String onboardingStep,
        String preferredLanguage,
        boolean triagePaused,
        GmailConnectionStatusExtended gmailConnectionStatus) {

    public record GmailConnectionStatusExtended(
            String status, String ingestionHealth, String googleEmail) {}

    public static MeResponse from(
            CurrentUserProjection user,
            boolean triagePaused,
            GmailConnectionStatusExtended gmailStatus) {
        return new MeResponse(
                user.userId().toString(),
                user.tenantId().toString(),
                user.email(),
                user.onboardingStep(),
                user.preferredLanguage(),
                triagePaused,
                gmailStatus);
    }

    public static MeResponse from(
            CurrentUserProjection user,
            boolean triagePaused,
            GmailConnectionProjection gmailConnection) {
        return from(
                user,
                triagePaused,
                new GmailConnectionStatusExtended(
                        gmailConnection.status(),
                        gmailConnection.ingestionHealth(),
                        gmailConnection.googleEmail()));
    }
}

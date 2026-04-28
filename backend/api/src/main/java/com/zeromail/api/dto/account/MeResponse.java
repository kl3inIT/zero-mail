package com.zeromail.api.dto.account;

import com.zeromail.core.account.model.CurrentUserProjection;

public record MeResponse(String userId, String tenantId, String email, String onboardingStep, String preferredLanguage) {

    public static MeResponse from(CurrentUserProjection user) {
        return new MeResponse(
                user.userId().toString(),
                user.tenantId().toString(),
                user.email(),
                user.onboardingStep(),
                user.preferredLanguage());
    }
}

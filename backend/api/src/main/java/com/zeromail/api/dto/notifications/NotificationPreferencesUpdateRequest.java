package com.zeromail.api.dto.notifications;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NotificationPreferencesUpdateRequest(
        @NotNull Boolean digestEnabled, @Min(0) @Max(23) int digestSendHourLocal) {}

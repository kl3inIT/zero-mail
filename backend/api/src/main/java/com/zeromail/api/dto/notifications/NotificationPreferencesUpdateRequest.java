package com.zeromail.api.dto.notifications;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"digestEnabled", "digestSendHourLocal"})
public record NotificationPreferencesUpdateRequest(
        @NotNull Boolean digestEnabled, @Min(0) @Max(23) int digestSendHourLocal) {}

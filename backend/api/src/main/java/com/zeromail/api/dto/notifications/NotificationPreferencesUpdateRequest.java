package com.zeromail.api.dto.notifications;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"digestEnabled", "digestSendHourLocal", "digestSendDayOfWeek"})
public record NotificationPreferencesUpdateRequest(
        @NotNull Boolean digestEnabled,
        @Min(0) @Max(23) int digestSendHourLocal,
        @Schema(description = "ISO day-of-week: Monday=1 .. Sunday=7.") @Min(1) @Max(7) int digestSendDayOfWeek) {}

package com.zeromail.api.dto.calendar;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body for {@code PATCH /api/calendar/calendars/{id}/enabled}. Single boolean — no Bean Validation
 * needed; Jackson rejects malformed bodies before the controller runs.
 */
@Schema(requiredProperties = {"enabled"})
public record UpdateCalendarEnabledRequest(boolean enabled) {}

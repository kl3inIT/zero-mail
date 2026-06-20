package com.zeromail.api.dto.calendar;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of {@code PATCH /api/calendar/calendars/{id}/enabled} — exposes the number of preference
 * rows that were cascade-deleted on a toggle-off so the UI can surface "Removed N role
 * assignments".
 */
@Schema(requiredProperties = {"preferencesRemoved"})
public record CalendarToggleResponse(int preferencesRemoved) {}

package com.zeromail.api.dto.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Body for {@code PATCH /api/calendar/mailboxes/{mailboxId}/preferences}. FREEBUSY is multi-select;
 * EVENT_WRITE and BRIEF_SOURCE are single-select (nullable to clear). The list itself is
 * {@code @NotNull} (empty list is valid — clears FREEBUSY) so request body validation rejects
 * {@code null} explicitly with a 400 instead of silently treating it as empty.
 */
@Schema(requiredProperties = {"freebusyCalendarIds"})
public record UpdateMailboxCalendarPreferenceRequest(
        @NotNull List<UUID> freebusyCalendarIds,
        @Schema(nullable = true) UUID eventWriteCalendarId,
        @Schema(nullable = true) UUID briefSourceCalendarId) {}

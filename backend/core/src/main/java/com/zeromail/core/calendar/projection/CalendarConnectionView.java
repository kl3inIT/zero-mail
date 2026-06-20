package com.zeromail.core.calendar.projection;

import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-side projection of a single {@code calendar_connections} row joined with its sub-calendar
 * rows + the per-mailbox preference rows for the active mailbox. Built by {@code
 * CalendarConnectionService.list(...)} and consumed by the W2 REST surface.
 *
 * <p>Privacy: this is a read-side value object that exits {@code core} only via controller DTOs.
 * Logs of this projection never include {@code googleEmail}; the field stays only because the W3
 * settings page renders it on the connection card.
 */
public record CalendarConnectionView(
        UUID id,
        UUID tenantId,
        String googleEmail,
        CalendarConnectionStatus status,
        Instant connectedAt,
        Instant disconnectedAt,
        String googleProfileName,
        String googleProfilePictureUrl,
        List<CalendarView> calendars,
        List<MailboxCalendarPreferenceView> mailboxPreferences) {

    /**
     * Nested sub-calendar projection — one per {@code calendars} row attached to the connection.
     */
    public record CalendarView(
            UUID id,
            String externalCalendarId,
            String name,
            boolean isPrimary,
            boolean isEnabled,
            String timezone) {}
}

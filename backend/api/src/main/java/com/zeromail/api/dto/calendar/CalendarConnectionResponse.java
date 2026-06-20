package com.zeromail.api.dto.calendar;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.calendar.projection.CalendarConnectionView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * One {@code calendar_connections} row + its sub-calendar list + its per-mailbox preference list.
 * Variant fields ({@code disconnectedAt}, profile fields) are serialized only when non-null ({@link
 * JsonInclude.Include#NON_NULL}) so the TypeScript client can treat them as optional.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"id", "googleEmail", "status", "calendars", "preferences"})
public record CalendarConnectionResponse(
        String id,
        String googleEmail,
        @Schema(allowableValues = {"CONNECTED", "DISCONNECTED", "REVOKED"}) String status,
        @Schema(nullable = true) Instant connectedAt,
        @Schema(nullable = true) Instant disconnectedAt,
        @Schema(nullable = true) String googleProfileName,
        @Schema(nullable = true) String googleProfilePictureUrl,
        List<CalendarSubResponse> calendars,
        List<MailboxCalendarPreferenceResponse> preferences) {

    public static CalendarConnectionResponse from(CalendarConnectionView view) {
        return new CalendarConnectionResponse(
                view.id().toString(),
                view.googleEmail(),
                view.status().name(),
                view.connectedAt(),
                view.disconnectedAt(),
                view.googleProfileName(),
                view.googleProfilePictureUrl(),
                view.calendars().stream().map(CalendarSubResponse::from).toList(),
                view.mailboxPreferences().stream()
                        .map(MailboxCalendarPreferenceResponse::from)
                        .toList());
    }
}

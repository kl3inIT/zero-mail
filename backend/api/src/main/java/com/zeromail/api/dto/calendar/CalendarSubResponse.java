package com.zeromail.api.dto.calendar;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.calendar.projection.CalendarConnectionView.CalendarView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One sub-calendar in a {@link CalendarConnectionResponse}. The {@code timezone} is nullable —
 * Google's response sometimes omits it for synthetic calendars.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"id", "externalCalendarId", "name", "isPrimary", "isEnabled"})
public record CalendarSubResponse(
        String id,
        String externalCalendarId,
        String name,
        boolean isPrimary,
        boolean isEnabled,
        @Schema(nullable = true) String timezone) {

    public static CalendarSubResponse from(CalendarView view) {
        return new CalendarSubResponse(
                view.id().toString(),
                view.externalCalendarId(),
                view.name(),
                view.isPrimary(),
                view.isEnabled(),
                view.timezone());
    }
}

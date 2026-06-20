package com.zeromail.api.dto.calendar;

import com.zeromail.core.calendar.projection.MailboxCalendarPreferenceView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One per-(mailbox, role, calendar) tagging row. The {@code role} field is constrained at the
 * OpenAPI layer to the closed enum so the generated TypeScript client gets a precise union type.
 */
@Schema(requiredProperties = {"id", "mailboxId", "calendarConnectionId", "calendarId", "role"})
public record MailboxCalendarPreferenceResponse(
        String id,
        String mailboxId,
        String calendarConnectionId,
        String calendarId,
        @Schema(allowableValues = {"FREEBUSY", "EVENT_WRITE", "BRIEF_SOURCE"}) String role) {

    public static MailboxCalendarPreferenceResponse from(MailboxCalendarPreferenceView view) {
        return new MailboxCalendarPreferenceResponse(
                view.id().toString(),
                view.mailboxId().toString(),
                view.calendarConnectionId().toString(),
                view.calendarId().toString(),
                view.role().name());
    }
}

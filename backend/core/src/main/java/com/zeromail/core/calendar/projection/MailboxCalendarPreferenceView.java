package com.zeromail.core.calendar.projection;

import com.zeromail.core.calendar.domain.MailboxCalendarRole;
import java.util.UUID;

/**
 * Read-side projection of a single {@code mailbox_calendar_preferences} row — one (mailbox, role,
 * calendar) tagging.
 */
public record MailboxCalendarPreferenceView(
        UUID id,
        UUID mailboxId,
        UUID calendarConnectionId,
        UUID calendarId,
        MailboxCalendarRole role) {}

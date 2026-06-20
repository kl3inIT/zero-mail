package com.zeromail.core.calendar.usecases;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Replace-strategy command for {@code MailboxCalendarPreferenceService.updateForMailbox} — captures
 * the full desired preference state for a single mailbox in one record. FREEBUSY is multi-select
 * (the list may be empty); EVENT_WRITE and BRIEF_SOURCE are single-select (nullable to clear).
 */
public record UpdateMailboxCalendarPreferenceCommand(
        UUID mailboxId,
        List<UUID> freebusyCalendarIds,
        UUID eventWriteCalendarId,
        UUID briefSourceCalendarId) {

    public UpdateMailboxCalendarPreferenceCommand {
        Objects.requireNonNull(mailboxId, "mailboxId");
        Objects.requireNonNull(freebusyCalendarIds, "freebusyCalendarIds");
        freebusyCalendarIds = List.copyOf(freebusyCalendarIds);
    }
}

package com.zeromail.core.calendar.usecases;

import com.zeromail.core.calendar.domain.MailboxCalendarRole;
import com.zeromail.core.calendar.persistence.CalendarEntity;
import com.zeromail.core.calendar.persistence.CalendarRepository;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceEntity;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceRepository;
import com.zeromail.core.calendar.projection.MailboxCalendarPreferenceView;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-mailbox role assignments for Calendar sub-calendars (CAL-CONN-07 + Q3 lock).
 *
 * <p>Cardinality enforced both at the DB layer (partial unique indexes {@code
 * uq_mailbox_event_write}, {@code uq_mailbox_brief_source} from W0 changeset 133) and at the
 * service layer (pre-validation for friendlier error messages). FREEBUSY is multi-select; the
 * EVENT_WRITE and BRIEF_SOURCE fields on {@link UpdateMailboxCalendarPreferenceCommand} are
 * single-valued (nullable to clear).
 *
 * <p>Strategy: replace — {@link #updateForMailbox(UUID, UpdateMailboxCalendarPreferenceCommand)}
 * deletes every preference row for the mailbox then INSERTs fresh rows for each requested role. The
 * small per-mailbox N (typically &lt; 10 rows) makes this simpler than a per-row diff while still
 * tenant-safe.
 */
@Service
public class MailboxCalendarPreferenceService {

    private static final Logger log =
            LoggerFactory.getLogger(MailboxCalendarPreferenceService.class);

    private final MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository;
    private final CalendarRepository calendarRepository;

    public MailboxCalendarPreferenceService(
            MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository,
            CalendarRepository calendarRepository) {
        this.mailboxCalendarPreferenceRepository = mailboxCalendarPreferenceRepository;
        this.calendarRepository = calendarRepository;
    }

    @Transactional(readOnly = true)
    public List<MailboxCalendarPreferenceView> findAllForMailbox(UUID tenantId, UUID mailboxId) {
        return mailboxCalendarPreferenceRepository
                .findAllByMailboxIdAndTenantId(mailboxId, tenantId)
                .stream()
                .map(
                        preferenceRow ->
                                new MailboxCalendarPreferenceView(
                                        preferenceRow.getId(),
                                        preferenceRow.getMailboxId(),
                                        preferenceRow.getCalendarConnectionId(),
                                        preferenceRow.getCalendarId(),
                                        preferenceRow.getRole()))
                .toList();
    }

    @Transactional
    public List<MailboxCalendarPreferenceView> updateForMailbox(
            UUID tenantId, UpdateMailboxCalendarPreferenceCommand command) {
        UUID mailboxId = command.mailboxId();
        validateCalendarOwnership(tenantId, command);

        mailboxCalendarPreferenceRepository.deleteByMailboxIdAndTenantId(mailboxId, tenantId);

        for (UUID freebusyCalendarId : command.freebusyCalendarIds()) {
            CalendarEntity calendar = resolveOwnedEnabledCalendar(tenantId, freebusyCalendarId);
            mailboxCalendarPreferenceRepository.save(
                    new MailboxCalendarPreferenceEntity(
                            UUID.randomUUID(),
                            tenantId,
                            mailboxId,
                            calendar.getCalendarConnectionId(),
                            calendar.getId(),
                            MailboxCalendarRole.FREEBUSY));
        }
        if (command.eventWriteCalendarId() != null) {
            CalendarEntity calendar =
                    resolveOwnedEnabledCalendar(tenantId, command.eventWriteCalendarId());
            mailboxCalendarPreferenceRepository.save(
                    new MailboxCalendarPreferenceEntity(
                            UUID.randomUUID(),
                            tenantId,
                            mailboxId,
                            calendar.getCalendarConnectionId(),
                            calendar.getId(),
                            MailboxCalendarRole.EVENT_WRITE));
        }
        if (command.briefSourceCalendarId() != null) {
            CalendarEntity calendar =
                    resolveOwnedEnabledCalendar(tenantId, command.briefSourceCalendarId());
            mailboxCalendarPreferenceRepository.save(
                    new MailboxCalendarPreferenceEntity(
                            UUID.randomUUID(),
                            tenantId,
                            mailboxId,
                            calendar.getCalendarConnectionId(),
                            calendar.getId(),
                            MailboxCalendarRole.BRIEF_SOURCE));
        }
        log.info(
                "event=mailbox_calendar_preferences_updated tenantId={} mailboxId={} freebusyCount={} hasEventWrite={} hasBriefSource={}",
                tenantId,
                mailboxId,
                command.freebusyCalendarIds().size(),
                command.eventWriteCalendarId() != null,
                command.briefSourceCalendarId() != null);
        return findAllForMailbox(tenantId, mailboxId);
    }

    private void validateCalendarOwnership(
            UUID tenantId, UpdateMailboxCalendarPreferenceCommand command) {
        for (UUID freebusyCalendarId : command.freebusyCalendarIds()) {
            resolveOwnedEnabledCalendar(tenantId, freebusyCalendarId);
        }
        if (command.eventWriteCalendarId() != null) {
            resolveOwnedEnabledCalendar(tenantId, command.eventWriteCalendarId());
        }
        if (command.briefSourceCalendarId() != null) {
            resolveOwnedEnabledCalendar(tenantId, command.briefSourceCalendarId());
        }
    }

    private CalendarEntity resolveOwnedEnabledCalendar(UUID tenantId, UUID calendarId) {
        CalendarEntity calendar =
                calendarRepository
                        .findByIdAndTenantId(calendarId, tenantId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Calendar not found for tenant: " + calendarId));
        if (!calendar.isEnabled()) {
            // D-13: the UI only lists enabled calendars; this closes the race where the user
            // disabled a calendar between page load and PATCH.
            throw new IllegalArgumentException(
                    "Calendar is not enabled and cannot be assigned a role: " + calendarId);
        }
        return calendar;
    }
}

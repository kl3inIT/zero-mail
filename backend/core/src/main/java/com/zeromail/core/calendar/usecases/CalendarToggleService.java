package com.zeromail.core.calendar.usecases;

import com.zeromail.core.calendar.persistence.CalendarEntity;
import com.zeromail.core.calendar.persistence.CalendarRepository;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-sub-calendar {@code is_enabled} toggle (D-13, Pitfall 6).
 *
 * <p>Toggling off cascades-deletes every {@code mailbox_calendar_preferences} row that references
 * the calendar — the DB does NOT auto-cascade an {@code is_enabled} flag change. Returning the
 * deletion count lets the controller surface "Removed N role assignments" on the UI.
 */
@Service
public class CalendarToggleService {

    private static final Logger log = LoggerFactory.getLogger(CalendarToggleService.class);

    private final CalendarRepository calendarRepository;
    private final MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository;

    public CalendarToggleService(
            CalendarRepository calendarRepository,
            MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository) {
        this.calendarRepository = calendarRepository;
        this.mailboxCalendarPreferenceRepository = mailboxCalendarPreferenceRepository;
    }

    /**
     * @return preference rows deleted because the calendar was toggled off (always 0 when {@code
     *     enabled=true}).
     */
    @Transactional
    public int setEnabled(UUID tenantId, UUID calendarId, boolean enabled) {
        CalendarEntity calendar =
                calendarRepository
                        .findByIdAndTenantId(calendarId, tenantId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Calendar not found for tenant: " + calendarId));
        calendar.setEnabled(enabled);
        calendarRepository.save(calendar);
        int deletedPreferenceRows = 0;
        if (!enabled) {
            deletedPreferenceRows =
                    mailboxCalendarPreferenceRepository.deleteByCalendarIdAndTenantId(
                            calendarId, tenantId);
        }
        log.info(
                "event=calendar_enabled_toggled tenantId={} calendarId={} enabled={} preferenceRowsDeleted={}",
                tenantId,
                calendarId,
                enabled,
                deletedPreferenceRows);
        return deletedPreferenceRows;
    }
}

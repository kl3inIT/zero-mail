package com.zeromail.core.calendar.usecases;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.zeromail.core.calendar.domain.MailboxCalendarRole;
import com.zeromail.core.calendar.gateway.CalendarApiClientFactory;
import com.zeromail.core.calendar.persistence.CalendarEntity;
import com.zeromail.core.calendar.persistence.CalendarRepository;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceEntity;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceRepository;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Post-OAuth Google {@code calendarList.list()} ingest (RESEARCH.md lines 882-924, D-06).
 *
 * <p>Invoked by {@code CalendarOAuthSuccessHandler}'s AFTER_COMMIT side effect once the {@code
 * calendar_connections} row is durably saved. The service walks every Google {@code calendarList}
 * entry, upserts a {@code calendars} row per entry (UPSERT not INSERT so a re-connect of the same
 * Google account does not trip {@code uq_calendar_connection_external_id}), and per D-06 seeds
 * three {@code mailbox_calendar_preferences} rows for the primary calendar — one each of {@code
 * FREEBUSY}, {@code EVENT_WRITE}, {@code BRIEF_SOURCE} — bound to the {@code activeMailboxId} the
 * user clicked Connect from.
 *
 * <p><b>Pitfall 8 (Workspace OU policy) mitigation:</b> a {@code GoogleJsonResponseException(403)}
 * on the {@code calendarList} call (Workspace admin blocking access) is caught, logged as {@code
 * event=calendar_snapshot_workspace_policy_blocked}, and the connection stays {@code CONNECTED}
 * with an empty sub-calendar list — W3's UX handles the empty-state hint. A future {@code
 * last_error} column on {@code calendar_connections} (v1.5+) could surface this clearer; tracked in
 * {@code .planning/todos/2026-06-20-pitfall-8-workspace-policy-flag.md}.
 *
 * <p>Privacy: NEVER log {@code item.getSummary()} — calendar names may carry PII (e.g. "Personal
 * Doctor Appointments"). Logs carry only opaque UUIDs + counts.
 */
@Service
public class CalendarSnapshotIngestionService {

    private static final Logger log =
            LoggerFactory.getLogger(CalendarSnapshotIngestionService.class);

    private final CalendarApiClientFactory calendarApiClientFactory;
    private final CalendarRepository calendarRepository;
    private final MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository;

    public CalendarSnapshotIngestionService(
            CalendarApiClientFactory calendarApiClientFactory,
            CalendarRepository calendarRepository,
            MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository) {
        this.calendarApiClientFactory = calendarApiClientFactory;
        this.calendarRepository = calendarRepository;
        this.mailboxCalendarPreferenceRepository = mailboxCalendarPreferenceRepository;
    }

    /**
     * Per D-06: ingest every sub-calendar for the connection and tag the primary calendar with all
     * three roles for {@code activeMailboxId}. If {@code activeMailboxId} is {@code null} (no
     * active mailbox at OAuth time — should never happen in production because W3's settings page
     * binds it via the OAuth state), the calendars rows are still inserted but no D-06
     * default-roles are seeded — a future user action on the settings page can assign roles
     * explicitly.
     *
     * @throws IOException on transport failure or non-403 Google API error.
     */
    @Transactional
    public void ingestSnapshot(UUID tenantId, UUID calendarConnectionId, UUID activeMailboxId)
            throws IOException {
        Calendar calendarClient =
                calendarApiClientFactory.buildClientForCalendarConnection(
                        tenantId, calendarConnectionId);
        CalendarList result;
        try {
            result = calendarClient.calendarList().list().execute();
        } catch (GoogleJsonResponseException workspaceBlocked) {
            if (workspaceBlocked.getStatusCode() == 403) {
                log.warn(
                        "event=calendar_snapshot_workspace_policy_blocked tenantId={} calendarConnectionId={}",
                        tenantId,
                        calendarConnectionId);
                return;
            }
            throw workspaceBlocked;
        }
        if (result == null || result.getItems() == null || result.getItems().isEmpty()) {
            log.info(
                    "event=calendar_snapshot_empty tenantId={} calendarConnectionId={}",
                    tenantId,
                    calendarConnectionId);
            return;
        }

        Optional<CalendarEntity> primaryCalendarOptional = Optional.empty();
        int upsertedCalendarRows = 0;
        for (CalendarListEntry googleCalendar : result.getItems()) {
            CalendarEntity savedCalendar =
                    upsertCalendar(tenantId, calendarConnectionId, googleCalendar);
            upsertedCalendarRows++;
            if (savedCalendar.isPrimary()) {
                primaryCalendarOptional = Optional.of(savedCalendar);
            }
        }
        log.info(
                "event=calendar_snapshot_ingested tenantId={} calendarConnectionId={} calendars={}",
                tenantId,
                calendarConnectionId,
                upsertedCalendarRows);

        if (activeMailboxId == null) {
            log.info(
                    "event=calendar_snapshot_no_active_mailbox_skip_default_roles tenantId={} calendarConnectionId={}",
                    tenantId,
                    calendarConnectionId);
            return;
        }
        if (primaryCalendarOptional.isEmpty()) {
            log.info(
                    "event=calendar_snapshot_no_primary_skip_default_roles tenantId={} calendarConnectionId={}",
                    tenantId,
                    calendarConnectionId);
            return;
        }

        // D-06 default-on-connect: seed FREEBUSY + EVENT_WRITE + BRIEF_SOURCE for the active
        // mailbox
        // only. The cascade in CalendarConnectionService.disconnect() already deleted any prior
        // preference rows on disconnect, so a fresh re-connect of the same Google account against
        // the same mailbox proceeds without uq_mailbox_event_write / uq_mailbox_brief_source
        // collision.
        CalendarEntity primaryCalendar = primaryCalendarOptional.get();
        for (MailboxCalendarRole role : MailboxCalendarRole.values()) {
            mailboxCalendarPreferenceRepository.save(
                    new MailboxCalendarPreferenceEntity(
                            UUID.randomUUID(),
                            tenantId,
                            activeMailboxId,
                            calendarConnectionId,
                            primaryCalendar.getId(),
                            role));
        }
        log.info(
                "event=calendar_snapshot_default_roles_seeded tenantId={} calendarConnectionId={} activeMailboxId={} roleCount=3",
                tenantId,
                calendarConnectionId,
                activeMailboxId);
    }

    private CalendarEntity upsertCalendar(
            UUID tenantId, UUID calendarConnectionId, CalendarListEntry googleCalendar) {
        String externalCalendarId = googleCalendar.getId();
        String calendarName = googleCalendar.getSummary();
        String description = googleCalendar.getDescription();
        boolean isPrimary = Boolean.TRUE.equals(googleCalendar.getPrimary());
        String timezone = googleCalendar.getTimeZone();

        return calendarRepository
                .findByCalendarConnectionIdAndExternalCalendarIdAndTenantId(
                        calendarConnectionId, externalCalendarId, tenantId)
                .map(
                        existing -> {
                            existing.setName(calendarName);
                            existing.setDescription(description);
                            existing.setPrimary(isPrimary);
                            existing.setTimezone(timezone);
                            return calendarRepository.save(existing);
                        })
                .orElseGet(
                        () -> {
                            CalendarEntity freshRow =
                                    new CalendarEntity(
                                            UUID.randomUUID(),
                                            tenantId,
                                            calendarConnectionId,
                                            externalCalendarId,
                                            calendarName,
                                            isPrimary,
                                            true);
                            freshRow.setDescription(description);
                            freshRow.setTimezone(timezone);
                            return calendarRepository.save(freshRow);
                        });
    }
}

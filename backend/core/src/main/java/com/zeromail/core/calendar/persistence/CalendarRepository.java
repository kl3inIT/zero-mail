package com.zeromail.core.calendar.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link CalendarEntity}. Always tenant-scope reads. */
public interface CalendarRepository extends JpaRepository<CalendarEntity, UUID> {

    /** All sub-calendars (enabled or not) for the given connection — used by the W3 settings UI. */
    List<CalendarEntity> findAllByCalendarConnectionIdAndTenantId(
            UUID calendarConnectionId, UUID tenantId);

    /**
     * Enabled-only listing — used by the W2 free/busy lookup and the W4 brief composer when the
     * caller needs the subset the user has not toggled off in the settings page.
     */
    List<CalendarEntity> findAllByCalendarConnectionIdAndTenantIdAndEnabledTrue(
            UUID calendarConnectionId, UUID tenantId);

    /**
     * Used by {@code CalendarSnapshotIngestionService} to detect a re-ingest of an existing Google
     * {@code calendarList} item (the {@code external_calendar_id} is stable across re-connects so a
     * fresh INSERT would trip {@code uq_calendar_connection_external_id} on the second connect of
     * the same Google account). The service upserts: present → update name/description/primary
     * fields; absent → INSERT.
     */
    Optional<CalendarEntity> findByCalendarConnectionIdAndExternalCalendarIdAndTenantId(
            UUID calendarConnectionId, String externalCalendarId, UUID tenantId);

    /** Tenant-scoped lookup for ownership verification before write operations. */
    Optional<CalendarEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}

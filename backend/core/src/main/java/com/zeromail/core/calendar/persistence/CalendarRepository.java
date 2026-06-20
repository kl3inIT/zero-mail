package com.zeromail.core.calendar.persistence;

import java.util.List;
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
}

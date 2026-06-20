package com.zeromail.core.calendar.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Modulith integration event published AFTER_COMMIT of {@code
 * CalendarConnectionService.disconnect(...)} (Phase 12 W2, D-14 cascade).
 *
 * <p>Subscribers:
 *
 * <ul>
 *   <li><b>W2 (this plan)</b> — {@code CalendarConnectionService}'s own AFTER_COMMIT listener
 *       evicts the {@code CalendarApiClientFactory} access-token cache so a still-valid (~59 min
 *       TTL) cached token cannot keep issuing Calendar API calls against a connection the user just
 *       disconnected (T-12-02 mitigation, CASA V13.1.5).
 *   <li><b>Phase 13</b> — the free/busy cache listener will subscribe to evict cached availability
 *       windows keyed by {@code calendarConnectionId}.
 *   <li><b>Phase 14</b> — the booking-link listener will null out {@code
 *       booking_link.destination_calendar_id} columns referencing the disconnected connection
 *       (deferred per Phase 12 RESEARCH §Q5 because the {@code booking_link} table does not exist
 *       yet).
 * </ul>
 *
 * <p>Carries only opaque UUIDs + an event timestamp — never {@code googleEmail}, never any
 * sub-calendar names, never any refresh-token bytes (privacy invariant).
 */
public record CalendarConnectionDisconnected(
        UUID tenantId, UUID calendarConnectionId, Instant disconnectedAt) {

    public CalendarConnectionDisconnected {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(calendarConnectionId, "calendarConnectionId");
        Objects.requireNonNull(disconnectedAt, "disconnectedAt");
    }
}

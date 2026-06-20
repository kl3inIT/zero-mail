/**
 * Calendar domain — Google Calendar OAuth connection lifecycle, sub-calendar enumeration, per-role
 * mailbox preferences (FREEBUSY / EVENT_WRITE / BRIEF_SOURCE).
 *
 * <p>Introduced in v1.4 Phase 12 (Calendar Connection + Triage Foundation). W1 lands the OAuth
 * registration seam + connection bootstrap; W2 owns {@code CalendarConnectionService} (list,
 * disconnect cascade) + {@code CalendarSnapshotIngestionService}; W3 ships the frontend route; W4
 * adds text/calendar triage classification on {@code gmail_inbox_projection}; W5 wires the seeded
 * {@code system-calendar} rule template to the new {@code PRESET_CALENDAR} matcher.
 *
 * <p>The {@code calendar_connections} row is workspace-shared (no {@code gmail_connection_id} FK —
 * CAL-CONN-06 invariant). One Google Calendar grant per workspace, with per-mailbox preferences
 * stored in {@code mailbox_calendar_preferences} (the {@code mailbox_id} column carries the {@code
 * gmail_connections.id} UUID; the JPA layer holds it as a plain {@code UUID}, not an entity
 * reference, so this module does NOT depend on Gmail at the Modulith metadata layer).
 *
 * <p><b>Modulith dependency rationale:</b>
 *
 * <ul>
 *   <li>{@code tenant} — every entity is tenant-owned via {@code AbstractTenantOwnedEntity}.
 *   <li>{@code shared :: lang} — {@code IdentifiedEnum} contract for {@code
 *       CalendarConnectionStatus} / {@code MailboxCalendarRole}.
 *   <li>{@code shared :: persistence} — {@code AbstractTenantOwnedEntity} base class.
 *   <li>{@code shared :: exception} — {@code BusinessException} base for {@code
 *       CalendarConnectionNotOwnedException} / {@code CalendarDisconnectedException}.
 *   <li>{@code shared :: error} — {@code ErrorCodes} dotted-hierarchy constants.
 *   <li>{@code oauth :: token} — {@code OAuthTokenStore} facade (W0) for AES-GCM refresh-token
 *       encrypt/decrypt with {@code RowDiscriminator.CALENDAR_CONNECTION}.
 * </ul>
 *
 * <p>W4 will introduce a read/write edge to {@code core.inbox} for {@code
 * gmail_inbox_projection.message_class} / {@code event_dt}; that allowedDependency is added in the
 * W4 changeset, not pre-declared here (no Modulith metadata churn until the import actually
 * appears).
 */
@ApplicationModule(
        displayName = "Calendar",
        allowedDependencies = {
            "tenant",
            "shared :: lang",
            "shared :: persistence",
            "shared :: exception",
            "shared :: error",
            "oauth :: token"
        })
package com.zeromail.core.calendar;

import org.springframework.modulith.ApplicationModule;

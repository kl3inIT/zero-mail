package com.zeromail.core.calendar.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Per-connection sub-calendar row — one per item Google's {@code calendarList.list()} returns at
 * connect time. The W2 {@code CalendarSnapshotIngestionService} owns the actual ingestion; this
 * entity is declared in W1 so the repository + Modulith metadata are settled before W2 begins.
 *
 * <p>Mirrors the Gmail precedent of carrying a UUID FK ({@code calendarConnectionId}) rather than a
 * JPA back-reference, keeping the lookup path explicit and Modulith-friendly.
 */
@Entity
@Table(name = "calendars")
public class CalendarEntity extends AbstractTenantOwnedEntity {

    @Column(name = "calendar_connection_id", nullable = false)
    private UUID calendarConnectionId;

    @Column(name = "external_calendar_id", nullable = false, columnDefinition = "text")
    private String externalCalendarId;

    @Column(name = "name", length = 512)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "timezone", length = 64)
    private String timezone;

    protected CalendarEntity() {}

    public CalendarEntity(
            UUID id,
            UUID tenantId,
            UUID calendarConnectionId,
            String externalCalendarId,
            String name,
            boolean primary,
            boolean enabled) {
        super(id, tenantId);
        this.calendarConnectionId = calendarConnectionId;
        this.externalCalendarId = externalCalendarId;
        this.name = name;
        this.primary = primary;
        this.enabled = enabled;
    }

    public UUID getCalendarConnectionId() {
        return calendarConnectionId;
    }

    public String getExternalCalendarId() {
        return externalCalendarId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}

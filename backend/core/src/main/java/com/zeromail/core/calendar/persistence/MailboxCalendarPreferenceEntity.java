package com.zeromail.core.calendar.persistence;

import com.zeromail.core.calendar.domain.MailboxCalendarRole;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Per-(mailbox, role, calendar) preference join row. {@code mailbox_id} is the {@code
 * gmail_connections.id} of the mailbox the role applies to; the FK is enforced at the DB layer via
 * Liquibase 133 ({@code fk_mcp_mailbox}). The JPA layer holds it as a plain {@code UUID} so this
 * module does NOT take a Modulith dependency on {@code core.gmail}.
 *
 * <p><b>Cardinality (CAL-CONN-07 + Q3 lock, enforced by Liquibase 133 partial indexes):</b>
 * FREEBUSY is multi-select per mailbox; EVENT_WRITE and BRIEF_SOURCE are single-select per mailbox.
 * See {@link MailboxCalendarRole} class JavaDoc for the matrix.
 */
@Entity
@Table(name = "mailbox_calendar_preferences")
public class MailboxCalendarPreferenceEntity extends AbstractTenantOwnedEntity {

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Column(name = "calendar_connection_id", nullable = false)
    private UUID calendarConnectionId;

    @Column(name = "calendar_id", nullable = false)
    private UUID calendarId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private MailboxCalendarRole role;

    protected MailboxCalendarPreferenceEntity() {}

    public MailboxCalendarPreferenceEntity(
            UUID id,
            UUID tenantId,
            UUID mailboxId,
            UUID calendarConnectionId,
            UUID calendarId,
            MailboxCalendarRole role) {
        super(id, tenantId);
        this.mailboxId = mailboxId;
        this.calendarConnectionId = calendarConnectionId;
        this.calendarId = calendarId;
        this.role = role;
    }

    public UUID getMailboxId() {
        return mailboxId;
    }

    public UUID getCalendarConnectionId() {
        return calendarConnectionId;
    }

    public UUID getCalendarId() {
        return calendarId;
    }

    public MailboxCalendarRole getRole() {
        return role;
    }

    public void setRole(MailboxCalendarRole role) {
        this.role = role;
    }
}

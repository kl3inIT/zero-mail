package com.zeromail.core.calendar.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Per-mailbox role a Calendar contributes (CAL-CONN-07 + Phase 12 Q3 lock). Implements {@link
 * IdentifiedEnum}; roles are unordered (FREEBUSY is not "less than" EVENT_WRITE in any sortable
 * sense), so per D-B5 we do NOT extend {@link com.zeromail.core.shared.lang.OrderedEnum}.
 *
 * <p><b>D-C2 invariant:</b> {@link #id()} returns {@link Enum#name()} so
 * {@code @Enumerated(EnumType.STRING)} continues to persist the canonical id.
 *
 * <p><b>Cardinality matrix (enforced at the DB layer in Liquibase changeset 133):</b>
 *
 * <ul>
 *   <li>{@code FREEBUSY} — multi-select per mailbox (one mailbox may opt several calendars into the
 *       free/busy probe so meeting drafts respect every signal); the join row uniqueness comes from
 *       {@code uq_mailbox_role_calendar} but no per-role partial index restricts cardinality.
 *   <li>{@code EVENT_WRITE} — single-select per mailbox; enforced by partial unique index {@code
 *       uq_mailbox_event_write} on {@code (mailbox_id) WHERE role = 'EVENT_WRITE'}.
 *   <li>{@code BRIEF_SOURCE} — single-select per mailbox; enforced by partial unique index {@code
 *       uq_mailbox_brief_source} on {@code (mailbox_id) WHERE role = 'BRIEF_SOURCE'}.
 * </ul>
 */
public enum MailboxCalendarRole implements IdentifiedEnum {

    /**
     * Calendar contributes free/busy time blocks for this mailbox's AI drafts (Phase 13).
     * Multi-select per mailbox — a user may tag work + personal + team calendars all as FREEBUSY so
     * the meeting-time suggestion respects every block.
     */
    FREEBUSY,

    /**
     * Calendar receives event writes (Phase 14 booking-page confirmations and the {@code
     * propose_meeting} rule action). Single-select per mailbox — enforced by partial unique index
     * {@code uq_mailbox_event_write}. One write destination per mailbox keeps drafts unambiguous.
     */
    EVENT_WRITE,

    /**
     * Calendar contributes events for meeting briefs (Phase 16 prep-time digests). Single-select
     * per mailbox — enforced by partial unique index {@code uq_mailbox_brief_source}. One brief
     * source per mailbox keeps the digest content owner-specific.
     */
    BRIEF_SOURCE;

    @Override
    public String id() {
        return name();
    }

    /** Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id. */
    public static MailboxCalendarRole fromId(String id) {
        return Stream.of(values())
                .filter(role -> role.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown MailboxCalendarRole id: " + id));
    }
}

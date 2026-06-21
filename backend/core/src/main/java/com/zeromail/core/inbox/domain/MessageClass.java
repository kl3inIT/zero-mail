package com.zeromail.core.inbox.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Calendar-aware message classification (Phase 12 W4, D-11).
 *
 * <p>NULL on the {@code gmail_inbox_projection.message_class} column correctly means "not a
 * calendar message"; callers MUST NOT default a NULL column to any of these values. The W4 {@code
 * CalendarMessageClassifier} only writes one of these ids when the Gmail message carries a {@code
 * text/calendar} MIME part whose ical4j-parsed METHOD maps to a known RFC 5546 verb:
 *
 * <ul>
 *   <li>{@link #INVITE} — {@code METHOD:REQUEST} (RFC 5546). Per {@code
 *       <open_questions_from_research>} Q2, all initial-or-reschedule REQUESTs map to {@code
 *       INVITE} in v1.4; RESCHEDULE detection (which requires UID lookup) is deferred.
 *   <li>{@link #CANCEL} — {@code METHOD:CANCEL} (RFC 5546).
 *   <li>{@link #RESCHEDULE} — Reserved for follow-up phase v1.5+; Phase 12 worker NEVER writes this
 *       value. The enum entry + column stay in place so a later phase can populate without a schema
 *       migration. Distinguishing initial vs reschedule for {@code METHOD:REQUEST} requires a
 *       per-thread UID lookup that is not yet implemented.
 *   <li>{@link #RSVP} — {@code METHOD:REPLY} (RFC 5546).
 * </ul>
 *
 * <p>D-12 inbox pinning derives {@code pin_until = event_dt + 24h} on the fly when this column is
 * non-null; no separate {@code pinned_until} column exists by design.
 */
public enum MessageClass implements IdentifiedEnum {
    INVITE,
    CANCEL,
    RESCHEDULE,
    RSVP;

    @Override
    public String id() {
        return name();
    }

    /**
     * Fail-loud lookup per CONVENTIONS.md §4 (D-B5 unordered identity). Throws {@link
     * NoSuchElementException} on any unknown id — callers must therefore never feed a NULL column
     * value here (use the nullable getter on the entity instead).
     */
    public static MessageClass fromId(String id) {
        return Stream.of(values())
                .filter(messageClass -> messageClass.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown MessageClass id: " + id));
    }
}

package com.zeromail.core.inbox.usecases;

import com.zeromail.core.inbox.domain.MessageClass;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Projection-sourced inbox list item — same shape as {@code
 * RecentInboxReadService.RecentInboxMessage} so the controller can serve either source via a single
 * page response.
 *
 * <p>Fields the projection does not store come back as their empty equivalents:
 *
 * <ul>
 *   <li>{@code to} / {@code cc} — projection is per-message metadata only (no recipient list);
 *       always empty list.
 *   <li>{@code labels} — projection stores {@code labelIds} but not Gmail's pretty {@code name};
 *       Wave 1 may enrich from a labels.list cache. Wave 0 leaves this empty.
 * </ul>
 *
 * <p>The {@code from} field is synthesized at decrypt time: {@code "DisplayName" <email>} when the
 * display name is present, otherwise the raw email.
 *
 * <p>Phase 12 G1: the trailing {@code messageClass} + {@code eventDt} carry W4's calendar
 * classification ({@code gmail_inbox_projection.message_class} / {@code event_dt}, written by the
 * worker {@code CalendarMessageClassifier}). Both are {@link Optional} and obey the pair-set
 * invariant the entity's {@code setCalendarClassification} enforces upstream: either both present
 * (a calendar invite/cancel/reschedule/RSVP) or both empty (every non-calendar row). The live-Gmail
 * fallback path has no projection columns, so those rows arrive here as empty too.
 */
public record InboxProjectionMessage(
        String gmailMessageId,
        String gmailThreadId,
        String subject,
        String snippet,
        String from,
        List<String> to,
        List<String> cc,
        Instant receivedAt,
        List<String> labelIds,
        List<InboxProjectionLabel> labels,
        boolean unread,
        boolean hasAttachment,
        Optional<MessageClass> messageClass,
        Optional<Instant> eventDt) {

    public InboxProjectionMessage {
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        subject = subject == null ? "" : subject;
        snippet = snippet == null ? "" : snippet;
        from = from == null ? "" : from;
        to = List.copyOf(to);
        cc = List.copyOf(cc);
        labelIds = List.copyOf(labelIds);
        labels = List.copyOf(labels);
        messageClass = messageClass == null ? Optional.empty() : messageClass;
        eventDt = eventDt == null ? Optional.empty() : eventDt;
        if (messageClass.isPresent() != eventDt.isPresent()) {
            throw new IllegalArgumentException(
                    "messageClass and eventDt must be set together (pair-set calendar invariant)");
        }
    }
}

package com.zeromail.core.inbox.usecases;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Projection-sourced inbox list item — same shape as {@code
 * RecentInboxReadService.RecentInboxMessage} so the controller can serve either source via a single
 * page response.
 *
 * <p>Fields the projection does not store come back as their empty equivalents:
 * <ul>
 *   <li>{@code to} / {@code cc} — projection is per-message metadata only (no recipient list);
 *       always empty list.
 *   <li>{@code labels} — projection stores {@code labelIds} but not Gmail's pretty {@code name};
 *       Wave 1 may enrich from a labels.list cache. Wave 0 leaves this empty.
 * </ul>
 *
 * <p>The {@code from} field is synthesized at decrypt time: {@code "DisplayName" <email>} when the
 * display name is present, otherwise the raw email.
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
        boolean hasAttachment) {

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
    }
}

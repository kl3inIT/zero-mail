package com.zeromail.core.cleanup.projection;

import java.time.Instant;
import java.util.Objects;

/**
 * Full message body for the Stats dialog preview pane (UNS-stats-03). Fetched live from Gmail with
 * {@code messages.get format=full} only when the user clicks an email row — never persisted.
 *
 * <p>Privacy: this projection is intentionally short-lived. The FE keeps it in TanStack Query cache
 * for 5 minutes max; no log line in this pipeline includes {@code htmlBody} or {@code plainBody}.
 * The body-content ban (ARCH-02) does NOT trigger here because the email content never reaches
 * {@code chat_message.parts} / {@code assistant_pending_action} — it goes straight from Gmail →
 * HTTP response → FE iframe sandbox.
 *
 * <p>{@code htmlBody} is the raw {@code text/html} part decoded from base64url (never
 * server-sanitized — FE renders inside a sandboxed iframe). {@code plainBody} is the fallback
 * {@code text/plain} part when no HTML alternative exists.
 */
public record SenderMessageBody(
        String gmailMessageId,
        String subject,
        String fromHeader,
        Instant internalDate,
        String htmlBody,
        String plainBody) {

    public SenderMessageBody {
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(internalDate, "internalDate must not be null");
        subject = Objects.requireNonNullElse(subject, "");
        fromHeader = Objects.requireNonNullElse(fromHeader, "");
    }
}

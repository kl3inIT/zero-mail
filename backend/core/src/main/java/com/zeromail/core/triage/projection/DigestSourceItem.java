package com.zeromail.core.triage.projection;

import java.time.Instant;

/**
 * Privacy-clean pointer to a message a rule filed into the digest, read from {@code triage_audit}.
 *
 * <p>Every field here is already-sanitized audit metadata: the Gmail message/thread ids, the
 * sanitized subject excerpt and sanitized sender email captured at triage time, and the rule-name
 * snapshot used to group the digest into IZ-style sections. There is intentionally no body field —
 * the weekly content digest fetches the message body fresh from Gmail at send time, summarizes it
 * in memory, and never persists either the body or the summary.
 */
public record DigestSourceItem(
        String gmailMessageId,
        String gmailThreadId,
        String sanitizedSubject,
        String sanitizedSenderEmail,
        String ruleNameSnapshot,
        Instant appliedAt) {}

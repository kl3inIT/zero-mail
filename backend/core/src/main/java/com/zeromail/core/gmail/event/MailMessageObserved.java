package com.zeromail.core.gmail.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event consumed by core.triage when Gmail ingestion observes a new inbox message.
 * Carries only stable Gmail ids and a timestamp; never subject, snippet, body, or sender display
 * name.
 */
public record MailMessageObserved(
        UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt) {}

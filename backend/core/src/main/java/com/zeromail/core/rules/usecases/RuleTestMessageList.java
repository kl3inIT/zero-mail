package com.zeromail.core.rules.usecases;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight listing of recent Gmail messages for the rules test tab. Carries only sanitized
 * display metadata — no rule evaluation, no LLM call, no credit cost. The user picks a row (or
 * "test all") to run the per-message evaluation, which is the billable step.
 */
public record RuleTestMessageList(List<Message> messages) {

    public RuleTestMessageList {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
    }

    public record Message(
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSenderEmail,
            String sanitizedSenderDomain,
            String sanitizedSubjectExcerpt,
            Instant internalDate,
            List<String> gmailLabelIds) {

        public Message {
            Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
            Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
            sanitizedSenderEmail = Objects.requireNonNullElse(sanitizedSenderEmail, "");
            sanitizedSenderDomain = Objects.requireNonNullElse(sanitizedSenderDomain, "");
            sanitizedSubjectExcerpt = Objects.requireNonNullElse(sanitizedSubjectExcerpt, "");
            Objects.requireNonNull(internalDate, "internalDate must not be null");
            gmailLabelIds =
                    List.copyOf(
                            Objects.requireNonNull(
                                    gmailLabelIds, "gmailLabelIds must not be null"));
        }
    }
}

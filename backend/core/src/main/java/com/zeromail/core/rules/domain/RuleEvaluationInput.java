package com.zeromail.core.rules.domain;

import com.zeromail.core.inbox.domain.MessageClass;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record RuleEvaluationInput(
        String sanitizedSenderEmail,
        String sanitizedSenderDomain,
        List<String> sanitizedToRecipientEmails,
        List<String> sanitizedCcRecipientEmails,
        String sanitizedSubjectExcerpt,
        List<String> gmailLabelIds,
        List<String> gmailCategories,
        Instant internalDate,
        Instant observedAt,
        boolean hasAttachment,
        boolean listUnsubscribePresent,
        boolean newsletterIndicatorPresent,
        boolean autoReplyIndicatorPresent,
        Optional<Boolean> sanitizedBodyEvidencePresent,
        Set<String> bodyDerivedFlags,
        Optional<MessageClass> messageClass) {

    public RuleEvaluationInput {
        sanitizedSenderEmail = normalizeNullableText(sanitizedSenderEmail);
        sanitizedSenderDomain = normalizeNullableText(sanitizedSenderDomain);
        sanitizedToRecipientEmails = normalizedList(sanitizedToRecipientEmails);
        sanitizedCcRecipientEmails = normalizedList(sanitizedCcRecipientEmails);
        sanitizedSubjectExcerpt = Objects.requireNonNullElse(sanitizedSubjectExcerpt, "");
        gmailLabelIds = copiedTextList(gmailLabelIds);
        gmailCategories = normalizedList(gmailCategories);
        Objects.requireNonNull(internalDate, "internalDate must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        sanitizedBodyEvidencePresent =
                Objects.requireNonNullElseGet(sanitizedBodyEvidencePresent, Optional::empty);
        bodyDerivedFlags = copiedTextSet(bodyDerivedFlags);
        messageClass = Objects.requireNonNullElseGet(messageClass, Optional::empty);
    }

    /**
     * Back-compat constructor for callers built before Phase 12 W5 added the calendar {@code
     * messageClass} field. New call sites should pass {@code messageClass} explicitly so the W5
     * {@code PresetCalendarMatcher} can fire deterministically; legacy call sites delegate to
     * {@code Optional.empty()} and behave exactly as before (the PRESET matcher returns NOT_MATCHED
     * for any input without a known calendar classification).
     */
    public RuleEvaluationInput(
            String sanitizedSenderEmail,
            String sanitizedSenderDomain,
            List<String> sanitizedToRecipientEmails,
            List<String> sanitizedCcRecipientEmails,
            String sanitizedSubjectExcerpt,
            List<String> gmailLabelIds,
            List<String> gmailCategories,
            Instant internalDate,
            Instant observedAt,
            boolean hasAttachment,
            boolean listUnsubscribePresent,
            boolean newsletterIndicatorPresent,
            boolean autoReplyIndicatorPresent,
            Optional<Boolean> sanitizedBodyEvidencePresent,
            Set<String> bodyDerivedFlags) {
        this(
                sanitizedSenderEmail,
                sanitizedSenderDomain,
                sanitizedToRecipientEmails,
                sanitizedCcRecipientEmails,
                sanitizedSubjectExcerpt,
                gmailLabelIds,
                gmailCategories,
                internalDate,
                observedAt,
                hasAttachment,
                listUnsubscribePresent,
                newsletterIndicatorPresent,
                autoReplyIndicatorPresent,
                sanitizedBodyEvidencePresent,
                bodyDerivedFlags,
                Optional.empty());
    }

    public boolean hasGmailLabel(String labelId) {
        return gmailLabelIds.stream().anyMatch(existingLabelId -> existingLabelId.equals(labelId));
    }

    public boolean hasGmailCategory(String category) {
        String normalizedCategory = normalizeNullableText(category);
        return gmailCategories.stream()
                .map(RuleEvaluationInput::normalizeNullableText)
                .anyMatch(existingCategory -> existingCategory.equals(normalizedCategory));
    }

    private static List<String> normalizedList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(RuleEvaluationInput::normalizeNullableText)
                .toList();
    }

    private static List<String> copiedTextList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).toList();
    }

    private static Set<String> copiedTextSet(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(values.stream().filter(Objects::nonNull).toList()));
    }

    private static String normalizeNullableText(String text) {
        return Objects.requireNonNullElse(text, "").trim().toLowerCase(Locale.ROOT);
    }
}

package com.zeromail.core.rules.domain;

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
        Optional<Boolean> sanitizedBodyEvidencePresent,
        Set<String> bodyDerivedFlags) {

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

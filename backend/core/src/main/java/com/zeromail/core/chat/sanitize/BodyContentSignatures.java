package com.zeromail.core.chat.sanitize;

import java.util.List;
import java.util.regex.Pattern;

public final class BodyContentSignatures {

    public static final List<String> BODY_FIELD_NAMES =
            List.of(
                    "body",
                    "emailBody",
                    "messageBody",
                    "bodyHtml",
                    "bodyText",
                    "htmlBody",
                    "textBody");

    public static final List<Pattern> BODY_FIELD_PATTERNS =
            BODY_FIELD_NAMES.stream()
                    .map(Pattern::quote)
                    .map(
                            quotedName ->
                                    Pattern.compile(
                                            "^" + quotedName + "$", Pattern.CASE_INSENSITIVE))
                    .toList();

    private BodyContentSignatures() {}

    public static boolean matches(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        return BODY_FIELD_PATTERNS.stream()
                .anyMatch(bodyFieldPattern -> bodyFieldPattern.matcher(fieldName).matches());
    }
}

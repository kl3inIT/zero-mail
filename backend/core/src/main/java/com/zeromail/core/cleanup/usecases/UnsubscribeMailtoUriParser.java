package com.zeromail.core.cleanup.usecases;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 6068 {@code mailto:} URI parser — D-23 ("Recipient parsing từ mailto URI = Java URI built-in,
 * KHÔNG regex").
 *
 * <p>Built on {@link java.net.URI}. Splits the raw scheme-specific part at the first {@code ?},
 * URL-decodes the recipient and query parameters, and returns a structured record. Defaults subject
 * and body to {@code "unsubscribe"} per D-06 (RFC convention body for one-click mailto
 * unsubscribe).
 *
 * <p>Never uses {@code java.util.regex.Pattern} / {@code Matcher} — character-class operations are
 * confined to the JDK URI parser. This is verified by the {@code acceptance_criteria} grep in Plan
 * 05.
 *
 * <p>Static helper — no Spring bean. Callers: {@link UnsubscribeMailtoSender}.
 */
public final class UnsubscribeMailtoUriParser {

    private static final String DEFAULT_SUBJECT_AND_BODY = "unsubscribe";
    private static final String SCHEME_MAILTO = "mailto";
    private static final int MAX_RECIPIENT_LENGTH =
            320; // RFC 5321 §4.5.3.1.3 SMTP local + domain cap.

    /**
     * Structured parse result. {@code subject} and {@code body} fall back to {@code "unsubscribe"}.
     */
    public record ParsedMailto(String recipient, String subject, String body) {
        public ParsedMailto {
            Objects.requireNonNull(recipient, "recipient must not be null");
            Objects.requireNonNull(subject, "subject must not be null");
            Objects.requireNonNull(body, "body must not be null");
        }
    }

    private UnsubscribeMailtoUriParser() {
        // static helper — no instances.
    }

    /**
     * Parse a {@code mailto:} URI string per RFC 6068.
     *
     * @param rawMailtoValue raw URI value as observed in the {@code List-Unsubscribe} header (e.g.
     *     {@code mailto:unsub@provider.test?subject=Unsubscribe}).
     * @return parsed recipient + subject + body.
     * @throws IllegalArgumentException if {@code rawMailtoValue} is null, malformed, has a
     *     non-mailto scheme, or contains an unparseable recipient.
     */
    public static ParsedMailto parse(String rawMailtoValue) {
        if (rawMailtoValue == null || rawMailtoValue.isBlank()) {
            throw new IllegalArgumentException("rawMailtoValue must not be null or blank");
        }
        URI mailtoUri;
        try {
            mailtoUri = new URI(rawMailtoValue);
        } catch (URISyntaxException malformedUri) {
            throw new IllegalArgumentException(
                    "Malformed mailto URI: " + malformedUri.getMessage(), malformedUri);
        }
        String mailtoScheme = mailtoUri.getScheme();
        if (mailtoScheme == null || !SCHEME_MAILTO.equalsIgnoreCase(mailtoScheme)) {
            throw new IllegalArgumentException("Not a mailto URI: scheme=" + mailtoScheme);
        }
        String schemeSpecific = mailtoUri.getRawSchemeSpecificPart();
        if (schemeSpecific == null || schemeSpecific.isBlank()) {
            throw new IllegalArgumentException("Mailto URI has empty scheme-specific part");
        }
        int queryIndex = schemeSpecific.indexOf('?');
        String recipientPart =
                queryIndex < 0 ? schemeSpecific : schemeSpecific.substring(0, queryIndex);
        String queryString = queryIndex < 0 ? "" : schemeSpecific.substring(queryIndex + 1);

        String recipientEmail = URLDecoder.decode(recipientPart, StandardCharsets.UTF_8);
        if (recipientEmail.isBlank()) {
            throw new IllegalArgumentException("Mailto URI has empty recipient");
        }
        if (!recipientEmail.contains("@")) {
            throw new IllegalArgumentException(
                    "Mailto recipient missing '@': " + maskRecipient(recipientEmail));
        }
        if (recipientEmail.length() > MAX_RECIPIENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Mailto recipient exceeds " + MAX_RECIPIENT_LENGTH + " chars");
        }

        Map<String, String> queryParameters = parseQueryString(queryString);
        String subject = queryParameters.getOrDefault("subject", DEFAULT_SUBJECT_AND_BODY);
        String body = queryParameters.getOrDefault("body", DEFAULT_SUBJECT_AND_BODY);

        return new ParsedMailto(recipientEmail, subject, body);
    }

    private static Map<String, String> parseQueryString(String queryString) {
        Map<String, String> parameters = new HashMap<>();
        if (queryString.isEmpty()) {
            return parameters;
        }
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            String rawKey = equalsIndex < 0 ? pair : pair.substring(0, equalsIndex);
            String rawValue = equalsIndex < 0 ? "" : pair.substring(equalsIndex + 1);
            String decodedKey =
                    URLDecoder.decode(rawKey, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            String decodedValue = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            // First occurrence wins — defensive for duplicate keys per RFC 6068.
            parameters.putIfAbsent(decodedKey, decodedValue);
        }
        return parameters;
    }

    /** Privacy helper — mask all but the domain when logging a malformed recipient. */
    private static String maskRecipient(String recipientEmail) {
        int atIndex = recipientEmail.lastIndexOf('@');
        if (atIndex < 0) {
            return "***";
        }
        return "***@" + recipientEmail.substring(atIndex + 1);
    }
}

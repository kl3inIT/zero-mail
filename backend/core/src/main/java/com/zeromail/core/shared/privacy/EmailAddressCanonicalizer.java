package com.zeromail.core.shared.privacy;

import com.zeromail.core.shared.lang.Strings;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EmailAddressCanonicalizer {

    private static final Pattern PLAUSIBLE_EMAIL_PATTERN =
            Pattern.compile("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final int DISPLAY_NAME_MAX_LENGTH = 320;

    public String canonicalize(String rawSenderEmail) {
        String extractedAddress =
                extractAddress(Strings.requireText(rawSenderEmail, "senderEmail"));
        String canonicalizedEmail = extractedAddress.toLowerCase(Locale.ROOT);
        if (!PLAUSIBLE_EMAIL_PATTERN.matcher(canonicalizedEmail).matches()) {
            throw new IllegalArgumentException("senderEmail is malformed");
        }
        return canonicalizedEmail;
    }

    /**
     * Extract the display-name portion of a From header value, e.g. {@code "John Doe"
     * <john@x>} → {@code "John Doe"}. Returns {@code Optional.empty()} when the header is
     * bare-address ({@code john@x}) or the display-name is blank/quote-only.
     *
     * <p>Same privacy class as {@link #canonicalize(String)} — the result is sanitized From-header
     * metadata, not email body content. Output is trimmed, surrounding quotes stripped, and
     * truncated to {@value #DISPLAY_NAME_MAX_LENGTH} characters so the {@code
     * mail_message_observed.sender_name varchar(320)} column never overflows.
     */
    public Optional<String> extractDisplayName(String rawFromHeader) {
        if (rawFromHeader == null) {
            return Optional.empty();
        }
        String trimmedHeader = rawFromHeader.trim();
        int leftAngle = trimmedHeader.lastIndexOf('<');
        if (leftAngle <= 0) {
            return Optional.empty();
        }
        String namePart = trimmedHeader.substring(0, leftAngle).trim();
        namePart = stripSurroundingQuotes(namePart);
        if (namePart.isEmpty()) {
            return Optional.empty();
        }
        if (namePart.length() > DISPLAY_NAME_MAX_LENGTH) {
            namePart = namePart.substring(0, DISPLAY_NAME_MAX_LENGTH);
        }
        return Optional.of(namePart);
    }

    private static String stripSurroundingQuotes(String value) {
        if (value.length() >= 2
                && value.charAt(0) == '"'
                && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static String extractAddress(String senderEmail) {
        int leftAngle = senderEmail.lastIndexOf('<');
        int rightAngle = senderEmail.lastIndexOf('>');
        if (leftAngle >= 0 || rightAngle >= 0) {
            if (leftAngle < 0 || rightAngle <= leftAngle) {
                throw new IllegalArgumentException("senderEmail is malformed");
            }
            return senderEmail.substring(leftAngle + 1, rightAngle).trim();
        }
        return senderEmail;
    }
}

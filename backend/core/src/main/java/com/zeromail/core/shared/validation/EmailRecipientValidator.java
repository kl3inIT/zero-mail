package com.zeromail.core.shared.validation;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class EmailRecipientValidator {

    private static final int MAX_RECIPIENTS = 10;
    private static final Pattern EMAIL_ADDRESS_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private EmailRecipientValidator() {}

    public static List<String> required(List<String> recipients, String fieldName) {
        List<String> normalizedRecipients = optional(recipients, fieldName);
        if (normalizedRecipients.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return normalizedRecipients;
    }

    public static List<String> optional(List<String> recipients, String fieldName) {
        if (recipients == null) {
            return List.of();
        }
        ArrayList<String> normalizedRecipients = new ArrayList<>();
        for (String recipient : recipients) {
            String trimmedRecipient =
                    Objects.requireNonNull(recipient, fieldName + " contains null").trim();
            if (trimmedRecipient.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not contain blank values");
            }
            // Normalize to bare addresses via RFC 822 parsing before the syntax check. This accepts
            // the formats the LLM rule compiler legitimately emits for a valid recipient — a
            // display
            // name ("Dat <dat@example.com>") or several addresses joined into one string
            // ("a@x.com, b@y.com") — and is consistent with the outbound send path, which already
            // parses recipients with InternetAddress. Without this, a perfectly valid email wrapped
            // in a display name was rejected as "invalid email address", so forward_email /
            // send_email rules could not be created. Bare names with no '@' still fail the pattern.
            for (String bareAddress : extractBareAddresses(trimmedRecipient, fieldName)) {
                if (!EMAIL_ADDRESS_PATTERN.matcher(bareAddress).matches()) {
                    throw new IllegalArgumentException(
                            fieldName + " contains invalid email address");
                }
                normalizedRecipients.add(bareAddress);
            }
        }
        if (normalizedRecipients.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException(fieldName + " has too many recipients");
        }
        return List.copyOf(normalizedRecipients);
    }

    private static List<String> extractBareAddresses(String rawRecipient, String fieldName) {
        InternetAddress[] parsedAddresses;
        try {
            parsedAddresses = InternetAddress.parse(rawRecipient, false);
        } catch (AddressException addressException) {
            throw new IllegalArgumentException(
                    fieldName + " contains invalid email address", addressException);
        }
        if (parsedAddresses.length == 0) {
            throw new IllegalArgumentException(fieldName + " contains invalid email address");
        }
        ArrayList<String> bareAddresses = new ArrayList<>(parsedAddresses.length);
        for (InternetAddress parsedAddress : parsedAddresses) {
            String bareAddress =
                    parsedAddress.getAddress() == null ? "" : parsedAddress.getAddress().trim();
            if (bareAddress.isBlank()) {
                throw new IllegalArgumentException(fieldName + " contains invalid email address");
            }
            bareAddresses.add(bareAddress);
        }
        return bareAddresses;
    }
}

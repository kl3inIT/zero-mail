package com.zeromail.core.shared.validation;

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
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException(fieldName + " has too many recipients");
        }
        ArrayList<String> normalizedRecipients = new ArrayList<>();
        for (String recipient : recipients) {
            String normalizedRecipient =
                    Objects.requireNonNull(recipient, fieldName + " contains null").trim();
            if (normalizedRecipient.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not contain blank values");
            }
            if (!EMAIL_ADDRESS_PATTERN.matcher(normalizedRecipient).matches()) {
                throw new IllegalArgumentException(fieldName + " contains invalid email address");
            }
            normalizedRecipients.add(normalizedRecipient);
        }
        return List.copyOf(normalizedRecipients);
    }
}

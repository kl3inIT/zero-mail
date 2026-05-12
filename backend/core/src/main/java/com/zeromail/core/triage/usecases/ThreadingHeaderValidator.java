package com.zeromail.core.triage.usecases;

import com.google.api.services.gmail.model.Message;
import com.zeromail.core.triage.exception.ThreadingHeaderInvalidException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Objects;

public final class ThreadingHeaderValidator {

    private ThreadingHeaderValidator() {}

    public static void validate(MimeMessage mimeMessage, String expectedThreadId)
            throws MessagingException {
        Objects.requireNonNull(mimeMessage, "mimeMessage must not be null");
        requireText(expectedThreadId);
        requireText(mimeMessage.getHeader("In-Reply-To", null));
        requireText(mimeMessage.getHeader("References", null));
        String subject = mimeMessage.getSubject();
        if (subject == null || !subject.regionMatches(true, 0, "Re:", 0, 3)) {
            throw new ThreadingHeaderInvalidException();
        }
        jakarta.mail.Address[] recipients =
                mimeMessage.getRecipients(jakarta.mail.Message.RecipientType.TO);
        if (recipients == null || recipients.length == 0) {
            throw new ThreadingHeaderInvalidException();
        }
    }

    public static void validate(
            MimeMessage mimeMessage, Message gmailMessage, String expectedThreadId)
            throws MessagingException {
        validate(mimeMessage, expectedThreadId);
        Objects.requireNonNull(gmailMessage, "gmailMessage must not be null");
        if (!expectedThreadId.equals(gmailMessage.getThreadId())) {
            throw new ThreadingHeaderInvalidException();
        }
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new ThreadingHeaderInvalidException();
        }
    }
}

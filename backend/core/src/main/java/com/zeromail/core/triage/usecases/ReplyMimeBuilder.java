package com.zeromail.core.triage.usecases;

import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.exception.MissingMessageIdException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;

public final class ReplyMimeBuilder {

    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    private ReplyMimeBuilder() {}

    public static String buildBase64UrlMime(ReplyHeaders replyHeaders, String body)
            throws MessagingException, IOException {
        MimeMessage mimeMessage = buildMimeMessage(replyHeaders, body);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(outputStream);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(outputStream.toByteArray());
    }

    public static MimeMessage buildMimeMessage(ReplyHeaders replyHeaders, String body)
            throws MessagingException {
        Objects.requireNonNull(replyHeaders, "replyHeaders must not be null");
        Objects.requireNonNull(body, "body must not be null");
        if (!replyHeaders.hasMessageId()) {
            throw new MissingMessageIdException();
        }

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        mimeMessage.setRecipients(
                jakarta.mail.Message.RecipientType.TO,
                InternetAddress.parse(replyHeaders.replyToAddress(), true));
        mimeMessage.setSubject(prefixReIfAbsent(replyHeaders.inboundSubject()), UTF_8);
        mimeMessage.setHeader("In-Reply-To", replyHeaders.inboundMessageId());
        mimeMessage.setHeader(
                "References",
                buildReferences(replyHeaders.priorReferences(), replyHeaders.inboundMessageId()));
        mimeMessage.setText(body, UTF_8);
        mimeMessage.saveChanges();
        return mimeMessage;
    }

    public static MimeMessage parseBase64UrlMime(String base64UrlMime) throws MessagingException {
        Objects.requireNonNull(base64UrlMime, "base64UrlMime must not be null");
        byte[] mimeBytes = Base64.getUrlDecoder().decode(base64UrlMime);
        return new MimeMessage(
                Session.getInstance(new Properties()), new ByteArrayInputStream(mimeBytes));
    }

    static String prefixReIfAbsent(String subject) {
        if (subject == null || subject.isBlank()) {
            return "Re:";
        }
        String trimmedSubject = subject.trim();
        if (trimmedSubject.regionMatches(true, 0, "Re:", 0, 3)) {
            return trimmedSubject;
        }
        return "Re: " + trimmedSubject;
    }

    static String buildReferences(String priorReferences, String inboundMessageId) {
        if (priorReferences == null || priorReferences.isBlank()) {
            return inboundMessageId;
        }
        return priorReferences.trim() + " " + inboundMessageId;
    }
}

package com.zeromail.core.composer.usecases;

import com.zeromail.core.composer.domain.ComposerMode;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Build and parse the MIME envelope for an inbox composer draft.
 *
 * <p>Distinct from {@code ReplyMimeBuilder} because the composer needs CC + BCC, multiple TO
 * addresses, an arbitrary user-edited subject, and the ability to omit threading headers for
 * forward-mode drafts.
 */
public final class ComposerMimeBuilder {

    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    private ComposerMimeBuilder() {}

    /**
     * Build the base64url-encoded RFC 822 payload that Gmail's drafts.create/update endpoints
     * expect under {@code Message.raw}. Empty body is allowed — Gmail accepts a draft with no body,
     * which matches what the composer shows immediately after the user opens it.
     */
    public static String buildBase64UrlMime(ComposerDraftUpsertCommand command)
            throws MessagingException, IOException {
        Objects.requireNonNull(command, "command must not be null");
        MimeMessage mimeMessage = buildMimeMessage(command);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(outputStream);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(outputStream.toByteArray());
    }

    static MimeMessage buildMimeMessage(ComposerDraftUpsertCommand command)
            throws MessagingException {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        applyRecipients(mimeMessage, RecipientType.TO, command.toAddresses());
        applyRecipients(mimeMessage, RecipientType.CC, command.ccAddresses());
        applyRecipients(mimeMessage, RecipientType.BCC, command.bccAddresses());
        mimeMessage.setSubject(command.subject(), UTF_8);
        if (command.mode().isReply() && command.rfc822MessageId() != null) {
            mimeMessage.setHeader("In-Reply-To", command.rfc822MessageId());
            mimeMessage.setHeader(
                    "References",
                    buildReferences(command.priorReferences(), command.rfc822MessageId()));
        }
        mimeMessage.setText(command.body() == null ? "" : command.body(), UTF_8);
        mimeMessage.saveChanges();
        return mimeMessage;
    }

    /** Parse a draft snapshot back from the {@code RAW} payload returned by drafts.get. */
    public static ParsedDraft parseRaw(String base64UrlMime)
            throws MessagingException, IOException {
        Objects.requireNonNull(base64UrlMime, "base64UrlMime must not be null");
        byte[] rawBytes = Base64.getUrlDecoder().decode(base64UrlMime);
        MimeMessage mimeMessage =
                new MimeMessage(
                        Session.getInstance(new Properties()), new ByteArrayInputStream(rawBytes));
        return new ParsedDraft(
                extractAddresses(mimeMessage, RecipientType.TO),
                extractAddresses(mimeMessage, RecipientType.CC),
                extractAddresses(mimeMessage, RecipientType.BCC),
                mimeMessage.getSubject() == null ? "" : mimeMessage.getSubject(),
                extractPlainTextBody(mimeMessage));
    }

    static void applyRecipients(MimeMessage mimeMessage, RecipientType type, String rawAddresses)
            throws MessagingException {
        if (rawAddresses == null || rawAddresses.isBlank()) {
            return;
        }
        InternetAddress[] parsedAddresses;
        try {
            parsedAddresses = InternetAddress.parse(rawAddresses, false);
        } catch (AddressException addressException) {
            throw new MessagingException(
                    "Composer draft recipient header is malformed", addressException);
        }
        if (parsedAddresses.length == 0) {
            return;
        }
        mimeMessage.setRecipients(type, parsedAddresses);
    }

    static String buildReferences(String priorReferences, String rfc822MessageId) {
        if (priorReferences == null || priorReferences.isBlank()) {
            return rfc822MessageId;
        }
        return priorReferences.trim() + " " + rfc822MessageId;
    }

    private static List<String> extractAddresses(MimeMessage mimeMessage, RecipientType type)
            throws MessagingException {
        jakarta.mail.Address[] addresses = mimeMessage.getRecipients(type);
        if (addresses == null || addresses.length == 0) {
            return List.of();
        }
        List<String> collected = new ArrayList<>(addresses.length);
        for (jakarta.mail.Address address : addresses) {
            if (address == null) {
                continue;
            }
            String text = address.toString();
            if (text != null && !text.isBlank()) {
                collected.add(text);
            }
        }
        return List.copyOf(collected);
    }

    private static String extractPlainTextBody(Part part) throws MessagingException, IOException {
        if (part == null) {
            return "";
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content == null ? "" : content.toString();
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int partIndex = 0; partIndex < multipart.getCount(); partIndex++) {
                    String partBody = extractPlainTextBody(multipart.getBodyPart(partIndex));
                    if (!partBody.isEmpty()) {
                        return partBody;
                    }
                }
            }
            return "";
        }
        if (part.isMimeType("text/*")) {
            Object content = part.getContent();
            return content == null ? "" : content.toString();
        }
        return "";
    }

    /**
     * Output of {@link #parseRaw(String)}. Recipients are RFC 822 strings as Gmail returned them.
     */
    public record ParsedDraft(
            List<String> toAddresses,
            List<String> ccAddresses,
            List<String> bccAddresses,
            String subject,
            String body) {
        public ParsedDraft {
            toAddresses = toAddresses == null ? List.of() : List.copyOf(toAddresses);
            ccAddresses = ccAddresses == null ? List.of() : List.copyOf(ccAddresses);
            bccAddresses = bccAddresses == null ? List.of() : List.copyOf(bccAddresses);
            subject = subject == null ? "" : subject;
            body = body == null ? "" : body;
        }
    }

    public static ComposerMode requireMode(ComposerMode mode) {
        return Objects.requireNonNull(mode, "mode must not be null");
    }
}

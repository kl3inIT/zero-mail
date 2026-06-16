package com.zeromail.core.outbound.usecases;

import com.google.api.services.gmail.Gmail;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.mailbox.MailboxRef;
import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * Builds a real RFC 822 forward of an existing Gmail message.
 *
 * <p>Both the rules engine and the chat assistant previously "forwarded" by composing a brand-new
 * email whose body was only the user's note — the original message content was silently dropped, so
 * recipients received an empty forward. This assembler fetches the source message in raw MIME form
 * and re-attaches it as a {@code message/rfc822} part, producing a lossless, standards-compliant
 * forward (the same on-the-wire shape a mail client emits for "forward as attachment"). The user's
 * note becomes the visible text body.
 *
 * <p>Privacy: the fetched raw original is extracted email content. It lives only in memory long
 * enough to assemble the outbound MIME and is never logged, never persisted to the DB, and never
 * placed into {@code chat_message.parts}. Only the user-authored note is persisted (by the caller),
 * which is permitted draft data under the draft-body carve-out.
 */
@Component
public class ForwardMessageAssembler {

    private static final String GMAIL_USER_ID = "me";

    private final GmailApiClientFactory gmailApiClientFactory;

    public ForwardMessageAssembler(GmailApiClientFactory gmailApiClientFactory) {
        this.gmailApiClientFactory =
                Objects.requireNonNull(
                        gmailApiClientFactory, "gmailApiClientFactory must not be null");
    }

    /**
     * Assemble a forward of {@code sourceMessageId} to the given recipients.
     *
     * @param mailboxRef mailbox whose Gmail connection owns the source message
     * @param sourceMessageId Gmail message id of the email being forwarded
     * @param to non-empty list of forward recipients
     * @param cc optional cc recipients (may be null/empty)
     * @param subject forward subject (already "Fwd:"-prefixed by the caller; blank ⇒ no Subject
     *     header, rendered by Gmail as "(no subject)")
     * @param note the user-authored note shown above the forwarded message (may be blank)
     * @param messageId deterministic RFC Message-ID header for idempotency
     * @return a Gmail {@link com.google.api.services.gmail.model.Message} with raw MIME set
     */
    public com.google.api.services.gmail.model.Message buildForward(
            MailboxRef mailboxRef,
            String sourceMessageId,
            List<String> to,
            List<String> cc,
            String subject,
            String note,
            String messageId)
            throws IOException {
        Objects.requireNonNull(mailboxRef, "mailboxRef must not be null");
        requireText(sourceMessageId, "sourceMessageId");
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("forward recipients (to) must not be empty");
        }
        requireText(messageId, "messageId");

        byte[] originalMimeBytes = fetchOriginalMimeBytes(mailboxRef, sourceMessageId);
        try {
            MimeMessage forwardMessage =
                    new MimeMessage(Session.getInstance(new Properties(), null));
            forwardMessage.setRecipients(
                    jakarta.mail.Message.RecipientType.TO,
                    InternetAddress.parse(String.join(",", to), false));
            if (cc != null && !cc.isEmpty()) {
                forwardMessage.setRecipients(
                        jakarta.mail.Message.RecipientType.CC,
                        InternetAddress.parse(String.join(",", cc), false));
            }
            // Blank-tolerant subject: omit the header entirely so Gmail renders "(no subject)".
            if (subject != null && !subject.isBlank()) {
                forwardMessage.setSubject(subject.trim(), StandardCharsets.UTF_8.name());
            }

            MimeMultipart forwardContent = new MimeMultipart("mixed");

            MimeBodyPart notePart = new MimeBodyPart();
            notePart.setText(note == null ? "" : note, StandardCharsets.UTF_8.name());
            forwardContent.addBodyPart(notePart);

            MimeBodyPart originalPart = new MimeBodyPart();
            ByteArrayDataSource originalDataSource =
                    new ByteArrayDataSource(originalMimeBytes, "message/rfc822");
            originalPart.setDataHandler(new DataHandler(originalDataSource));
            originalPart.setDisposition(MimeBodyPart.ATTACHMENT);
            originalPart.setFileName("forwarded-message.eml");
            forwardContent.addBodyPart(originalPart);

            forwardMessage.setContent(forwardContent);
            // setHeader before AND after saveChanges: saveChanges() rewrites Message-ID, so the
            // deterministic id must be re-applied afterwards to survive (matches the existing
            // builders' idempotency contract).
            forwardMessage.setHeader("Message-ID", messageId);
            forwardMessage.saveChanges();
            forwardMessage.setHeader("Message-ID", messageId);

            return new com.google.api.services.gmail.model.Message().setRaw(encode(forwardMessage));
        } catch (MessagingException messagingException) {
            throw new IOException("Unable to build forward MIME message", messagingException);
        }
    }

    private byte[] fetchOriginalMimeBytes(MailboxRef mailboxRef, String sourceMessageId)
            throws IOException {
        Gmail gmail = gmailApiClientFactory.buildClientForMailbox(mailboxRef);
        com.google.api.services.gmail.model.Message rawSource =
                gmail.users()
                        .messages()
                        .get(GMAIL_USER_ID, sourceMessageId)
                        .setFormat("raw")
                        .execute();
        String rawBase64Url = rawSource == null ? null : rawSource.getRaw();
        if (rawBase64Url == null || rawBase64Url.isBlank()) {
            throw new IOException("source message has no raw MIME content: " + sourceMessageId);
        }
        return Base64.getUrlDecoder().decode(rawBase64Url);
    }

    private static String encode(MimeMessage mimeMessage) throws IOException, MessagingException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(outputStream);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(outputStream.toByteArray());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

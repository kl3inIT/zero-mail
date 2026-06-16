package com.zeromail.core.triage.usecases;

import com.google.api.services.gmail.model.Message;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.outbound.usecases.ForwardMessageAssembler;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.domain.TriageActionResult;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboundRuleMessageBuilder {

    private final ForwardMessageAssembler forwardMessageAssembler;

    public OutboundRuleMessageBuilder(ForwardMessageAssembler forwardMessageAssembler) {
        this.forwardMessageAssembler =
                Objects.requireNonNull(
                        forwardMessageAssembler, "forwardMessageAssembler must not be null");
    }

    public Message build(
            TriageActionResult outboundIntent,
            ReplyHeaders replyHeaders,
            String inboundSubject,
            String sourceMessageId,
            MailboxRef mailboxRef,
            UUID tenantId,
            String idempotencyKey)
            throws IOException {
        Objects.requireNonNull(outboundIntent, "outboundIntent must not be null");
        Objects.requireNonNull(mailboxRef, "mailboxRef must not be null");
        return switch (outboundIntent) {
            case TriageActionResult.SendReply sendReply ->
                    buildReply(
                            replyHeaders,
                            sendReply.draftBody(),
                            sendReply.gmailThreadId(),
                            tenantId,
                            idempotencyKey);
            case TriageActionResult.ForwardEmail forwardEmail ->
                    forwardMessageAssembler.buildForward(
                            mailboxRef,
                            sourceMessageId,
                            forwardEmail.recipients(),
                            List.of(),
                            forwardSubject(inboundSubject),
                            forwardEmail.draftBody(),
                            deterministicMessageId(tenantId, idempotencyKey));
            case TriageActionResult.SendEmail sendEmail ->
                    buildNewEmail(
                            sendEmail.to(),
                            sendEmail.cc(),
                            sendEmail.bcc(),
                            sendEmail.subject(),
                            sendEmail.draftBody(),
                            tenantId,
                            idempotencyKey);
            default ->
                    throw new IllegalArgumentException(
                            "outboundIntent must be send_reply, forward_email, or send_email");
        };
    }

    private static Message buildReply(
            ReplyHeaders replyHeaders,
            String draftBody,
            String gmailThreadId,
            UUID tenantId,
            String idempotencyKey)
            throws IOException {
        try {
            MimeMessage mimeMessage = ReplyMimeBuilder.buildMimeMessage(replyHeaders, draftBody);
            mimeMessage.setHeader("Message-ID", deterministicMessageId(tenantId, idempotencyKey));
            String encodedMimeMessage = encodeMimeMessage(mimeMessage);
            Message gmailMessage =
                    new Message().setRaw(encodedMimeMessage).setThreadId(gmailThreadId);
            ThreadingHeaderValidator.validate(mimeMessage, gmailMessage, gmailThreadId);
            return gmailMessage;
        } catch (MessagingException messagingException) {
            throw new IOException("Unable to build outbound reply MIME", messagingException);
        }
    }

    private static Message buildNewEmail(
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String draftBody,
            UUID tenantId,
            String idempotencyKey)
            throws IOException {
        try {
            MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties(), null));
            mimeMessage.setRecipients(
                    jakarta.mail.Message.RecipientType.TO,
                    InternetAddress.parse(String.join(",", to), false));
            if (cc != null && !cc.isEmpty()) {
                mimeMessage.setRecipients(
                        jakarta.mail.Message.RecipientType.CC,
                        InternetAddress.parse(String.join(",", cc), false));
            }
            if (bcc != null && !bcc.isEmpty()) {
                mimeMessage.setRecipients(
                        jakarta.mail.Message.RecipientType.BCC,
                        InternetAddress.parse(String.join(",", bcc), false));
            }
            mimeMessage.setSubject(requireText(subject, "subject"), StandardCharsets.UTF_8.name());
            mimeMessage.setText(requireText(draftBody, "draftBody"), StandardCharsets.UTF_8.name());
            mimeMessage.saveChanges();
            mimeMessage.setHeader("Message-ID", deterministicMessageId(tenantId, idempotencyKey));

            return new Message().setRaw(encodeMimeMessage(mimeMessage));
        } catch (MessagingException messagingException) {
            throw new IOException("Unable to build outbound MIME message", messagingException);
        }
    }

    private static String encodeMimeMessage(MimeMessage mimeMessage)
            throws IOException, MessagingException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(outputStream);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(outputStream.toByteArray());
    }

    private static String deterministicMessageId(UUID tenantId, String idempotencyKey) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return "<%s.%s@zero-mail.invalid>"
                .formatted(tenantId, requireText(idempotencyKey, "idempotencyKey"));
    }

    private static String forwardSubject(String inboundSubject) {
        if (inboundSubject == null || inboundSubject.isBlank()) {
            return "Fwd:";
        }
        String trimmedSubject = inboundSubject.trim();
        return trimmedSubject.regionMatches(true, 0, "Fwd:", 0, 4)
                ? trimmedSubject
                : "Fwd: " + trimmedSubject;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

package com.zeromail.core.chat.confirm.send;

import com.google.api.services.gmail.model.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GmailMessageBuilder {

    public String generateMessageId(String tenantId, UUID chatId, String toolCallId) {
        return "<%s.%s.%s@zero-mail.invalid>"
                .formatted(
                        requireText(tenantId, "tenantId"),
                        Objects.requireNonNull(chatId, "chatId must not be null"),
                        requireText(toolCallId, "toolCallId"));
    }

    public Message build(AssistantSendCommand command, String preGeneratedMessageId) {
        Objects.requireNonNull(command, "command must not be null");
        String messageId = requireText(preGeneratedMessageId, "preGeneratedMessageId");
        try {
            MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties(), null));
            mimeMessage.setRecipients(
                    jakarta.mail.Message.RecipientType.TO,
                    InternetAddress.parse(requireText(command.to(), "to"), false));
            if (hasText(command.cc())) {
                mimeMessage.setRecipients(
                        jakarta.mail.Message.RecipientType.CC,
                        InternetAddress.parse(command.cc(), false));
            }
            if (hasText(command.bcc())) {
                mimeMessage.setRecipients(
                        jakarta.mail.Message.RecipientType.BCC,
                        InternetAddress.parse(command.bcc(), false));
            }
            mimeMessage.setSubject(
                    requireText(command.subject(), "subject"), StandardCharsets.UTF_8.name());
            mimeMessage.setText(
                    requireText(command.body().value(), "body"), StandardCharsets.UTF_8.name());
            putThreadingHeaders(mimeMessage, command);
            mimeMessage.setHeader("Message-ID", messageId);
            mimeMessage.saveChanges();
            mimeMessage.setHeader("Message-ID", messageId);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            mimeMessage.writeTo(outputStream);
            String encodedMimeMessage =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(outputStream.toByteArray());
            Message gmailMessage = new Message().setRaw(encodedMimeMessage);
            if (hasText(command.gmailThreadId())) {
                gmailMessage.setThreadId(command.gmailThreadId());
            }
            return gmailMessage;
        } catch (MessagingException | IOException messageBuildFailure) {
            throw new IllegalArgumentException(
                    "Unable to build Gmail MIME message", messageBuildFailure);
        }
    }

    private static void putThreadingHeaders(MimeMessage mimeMessage, AssistantSendCommand command)
            throws MessagingException {
        String threadingMessageId =
                firstText(command.inReplyToMessageId(), command.sourceMessageId());
        if (!hasText(threadingMessageId)) {
            return;
        }
        String normalizedMessageId = normalizeMessageId(threadingMessageId);
        mimeMessage.setHeader("In-Reply-To", normalizedMessageId);
        mimeMessage.setHeader("References", normalizedMessageId);
    }

    private static String normalizeMessageId(String rawMessageId) {
        String trimmedMessageId = requireText(rawMessageId, "rawMessageId");
        return trimmedMessageId.startsWith("<") ? trimmedMessageId : "<" + trimmedMessageId + ">";
    }

    private static String firstText(String firstValue, String secondValue) {
        return hasText(firstValue) ? firstValue : secondValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}

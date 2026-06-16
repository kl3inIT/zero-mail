package com.zeromail.core.draft.usecases;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GmailMessageHeaders;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.shared.lang.Strings;
import com.zeromail.core.triage.domain.ReplyHeaders;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DraftReplySourceLoader {

    private static final String USER_ID = "me";
    private static final List<String> REPLY_METADATA_HEADERS =
            List.of(
                    "Message-ID",
                    "References",
                    "In-Reply-To",
                    "Subject",
                    "From",
                    "Reply-To",
                    "Auto-Submitted");
    private static final String THREAD_FIELDS =
            "id,messages(id,threadId,labelIds,payload/headers,payload/mimeType,payload/body/data,"
                    + "payload/parts(mimeType,body/data,parts))";

    private final GmailApiClientFactory gmailApiClientFactory;
    private final GmailConnectionService gmailConnectionService;

    public DraftReplySourceLoader(
            GmailApiClientFactory gmailApiClientFactory,
            GmailConnectionService gmailConnectionService) {
        this.gmailApiClientFactory =
                Objects.requireNonNull(
                        gmailApiClientFactory, "gmailApiClientFactory must not be null");
        this.gmailConnectionService =
                Objects.requireNonNull(
                        gmailConnectionService, "gmailConnectionService must not be null");
    }

    public DraftReplySource load(UUID tenantId, String gmailThreadId) throws IOException {
        return load(primaryMailboxRefOrThrow(tenantId), gmailThreadId);
    }

    public DraftReplySource load(MailboxRef mailboxRef, String gmailThreadId) throws IOException {
        Objects.requireNonNull(mailboxRef, "mailboxRef must not be null");
        String threadId = Strings.requireText(gmailThreadId, "gmailThreadId");
        Gmail gmail = gmailApiClientFactory.buildClientForMailbox(mailboxRef);
        com.google.api.services.gmail.model.Thread gmailThread =
                gmail.users()
                        .threads()
                        .get(USER_ID, threadId)
                        .setFormat("full")
                        .setMetadataHeaders(REPLY_METADATA_HEADERS)
                        .setFields(THREAD_FIELDS)
                        .execute();
        List<Message> messages = gmailThread.getMessages();
        if (messages == null || messages.isEmpty()) {
            throw new IOException("Gmail thread contains no messages");
        }
        boolean threadHasSentLabel =
                messages.stream().anyMatch(message -> hasLabel(message, "SENT"));
        Message replyTarget = lastInboundMessage(messages).orElse(messages.getLast());
        MessagePart payload = replyTarget.getPayload();
        String subject = headerValue(payload, "Subject").orElse("");
        String replyToAddress =
                extractEmailAddress(
                        headerValue(payload, "Reply-To")
                                .or(() -> headerValue(payload, "From"))
                                .orElse(""));
        ReplyHeaders replyHeaders =
                ReplyHeaders.of(
                        headerValue(payload, "Message-ID").orElse(null),
                        headerValue(payload, "References")
                                .or(() -> headerValue(payload, "In-Reply-To"))
                                .orElse(null),
                        subject,
                        replyToAddress,
                        threadId);
        return new DraftReplySource(
                Strings.requireText(replyTarget.getId(), "gmailMessageId"),
                threadId,
                replyHeaders,
                extractReadableBody(payload),
                subject,
                hasLabel(replyTarget, "SENT"),
                threadHasSentLabel,
                isAutoReply(payload));
    }

    private MailboxRef primaryMailboxRefOrThrow(UUID tenantId) throws IOException {
        try {
            return gmailConnectionService
                    .primaryMailboxRef(tenantId)
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "Primary Gmail mailbox is required for draft source"));
        } catch (RuntimeException runtimeException) {
            throw new IOException("Unable to resolve primary Gmail mailbox", runtimeException);
        }
    }

    private static Optional<Message> lastInboundMessage(List<Message> messages) {
        for (int messageIndex = messages.size() - 1; messageIndex >= 0; messageIndex--) {
            Message message = messages.get(messageIndex);
            if (!hasLabel(message, "SENT")) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }

    private static boolean hasLabel(Message message, String labelId) {
        return message.getLabelIds() != null && message.getLabelIds().contains(labelId);
    }

    private static Optional<String> headerValue(MessagePart payload, String headerName) {
        return GmailMessageHeaders.firstValue(payload, headerName)
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    private static boolean isAutoReply(MessagePart payload) {
        return headerValue(payload, "Auto-Submitted")
                .map(value -> !"no".equalsIgnoreCase(value))
                .orElse(false);
    }

    private static String extractEmailAddress(String headerValue) {
        String sanitizedHeader = Objects.requireNonNullElse(headerValue, "").trim();
        int openAngleIndex = sanitizedHeader.indexOf('<');
        int closeAngleIndex = sanitizedHeader.indexOf('>');
        String emailAddress =
                openAngleIndex >= 0 && closeAngleIndex > openAngleIndex
                        ? sanitizedHeader.substring(openAngleIndex + 1, closeAngleIndex)
                        : sanitizedHeader;
        return emailAddress.toLowerCase(Locale.ROOT).trim();
    }

    private static String extractReadableBody(MessagePart payload) {
        if (payload == null) {
            return "";
        }
        String directBody = GmailMimeDecoder.decodedBody(payload);
        if (!directBody.isBlank() && GmailMimeDecoder.isReadableMimeType(payload.getMimeType())) {
            return directBody;
        }
        List<MessagePart> parts = payload.getParts();
        if (parts == null || parts.isEmpty()) {
            return directBody;
        }
        ArrayList<String> extractedParts = new ArrayList<>();
        for (MessagePart part : parts) {
            String extractedPart = extractReadableBody(part);
            if (!extractedPart.isBlank()) {
                extractedParts.add(extractedPart);
            }
        }
        return String.join("\n", extractedParts);
    }

    public record DraftReplySource(
            String gmailMessageId,
            String gmailThreadId,
            ReplyHeaders replyHeaders,
            String inboundRawHtml,
            String inboundSubject,
            boolean lastMessageFromIsTenant,
            boolean threadHasSentLabel,
            boolean lastMessageIsAutoReply) {

        public DraftReplySource {
            gmailMessageId = Strings.requireText(gmailMessageId, "gmailMessageId");
            gmailThreadId = Strings.requireText(gmailThreadId, "gmailThreadId");
            Objects.requireNonNull(replyHeaders, "replyHeaders must not be null");
            inboundRawHtml = Objects.requireNonNullElse(inboundRawHtml, "");
            inboundSubject = Objects.requireNonNullElse(inboundSubject, "");
        }
    }
}

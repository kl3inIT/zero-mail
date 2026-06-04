package com.zeromail.core.cleanup.usecases;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.zeromail.core.cleanup.projection.SenderMessageBody;
import com.zeromail.core.cleanup.projection.SenderMessageSummary;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GmailMessageHeaders;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stats dialog Gmail-live reader for the bulk-unsubscribe workflow (UNS-stats-02 + UNS-stats-03).
 * Inbox Zero parity: {@code apps/web/app/(app)/[emailAccountId]/stats/NewsletterModal.tsx} pipes
 * sender-specific listing through {@code /api/threads?fromEmail=...} which calls Gmail's native
 * {@code users.messages.list(q="from:...")}.
 *
 * <p>Privacy: this service is the only path that decodes raw email body bytes and is therefore
 * tightly scoped. Bodies are returned only inline in the HTTP response — no DB write, no log line
 * containing body content, no inclusion in chat/assistant pipelines (the body-content ban ARCH-02
 * still applies to those surfaces).
 */
@Service
@Transactional(readOnly = true)
public class SenderMessageReadService {

    private static final Logger log = LoggerFactory.getLogger(SenderMessageReadService.class);
    private static final String LIST_FIELDS = "messages(id,threadId)";
    private static final String SUMMARY_FIELDS =
            "id,threadId,labelIds,internalDate,snippet,payload/headers";
    private static final List<String> SUMMARY_HEADERS = List.of("Subject", "From");
    private static final int SUBJECT_MAX_LENGTH = 200;
    private static final int SNIPPET_MAX_LENGTH = 400;
    private static final int HARD_LIMIT_MESSAGES = 100;

    private final GmailConnectionRepository gmailConnectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;

    public SenderMessageReadService(
            GmailConnectionRepository gmailConnectionRepository,
            GmailApiClientFactory gmailApiClientFactory) {
        this.gmailConnectionRepository =
                Objects.requireNonNull(
                        gmailConnectionRepository, "gmailConnectionRepository must not be null");
        this.gmailApiClientFactory =
                Objects.requireNonNull(
                        gmailApiClientFactory, "gmailApiClientFactory must not be null");
    }

    /**
     * List recent messages from {@code senderEmail} via {@code users.messages.list q=from:...}.
     * Returns up to {@code limit} rows (hard-capped at {@value #HARD_LIMIT_MESSAGES}). When {@code
     * archivedOnly} is true the Gmail query is {@code from:X -in:inbox} — only archived messages
     * surface. Otherwise the query lists every message Gmail knows about.
     *
     * @throws SenderMessagesUnavailableException when Gmail is not reachable or the tenant has no
     *     active connection
     */
    public List<SenderMessageSummary> fetchMessagesFromSender(
            UUID tenantId,
            String senderEmail,
            boolean archivedOnly,
            int limit,
            Duration fetchBudget) {
        return fetchMessagesFromSender(
                tenantId, senderEmail, archivedOnly, limit, null, fetchBudget);
    }

    /**
     * Overload that restricts the Gmail listing to messages received after {@code now - window}.
     * Drives the stats dialog's 7d / 30d / 90d filter so the message list and the timeline chart
     * agree on the same physical messages — without the window the list returned every message
     * Gmail had from the sender even when the chart only counted the last seven days, giving the
     * user a confusing mismatch where the chart showed "3 emails this week" but the list scrolled
     * back into January.
     */
    public List<SenderMessageSummary> fetchMessagesFromSender(
            UUID tenantId,
            String senderEmail,
            boolean archivedOnly,
            int limit,
            Duration window,
            Duration fetchBudget) {
        Instant from = null;
        Instant to = null;
        if (window != null && !window.isNegative() && !window.isZero()) {
            to = Instant.now();
            from = to.minus(window);
        }
        return fetchMessagesFromSender(
                tenantId, senderEmail, archivedOnly, limit, from, to, fetchBudget);
    }

    /**
     * Range overload — appends both {@code after:<from-unix>} and {@code before:<to-unix>} to the
     * Gmail query so callers can list messages in an arbitrary historical window (e.g. May 1 – May
     * 15) instead of being capped to "last N days from now". {@code from} / {@code to} may be null
     * individually; null clauses are skipped so passing both null preserves the legacy unbounded
     * behaviour.
     */
    public List<SenderMessageSummary> fetchMessagesFromSender(
            UUID tenantId,
            String senderEmail,
            boolean archivedOnly,
            int limit,
            Instant from,
            Instant to,
            Duration fetchBudget) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(senderEmail, "senderEmail must not be null");
        Objects.requireNonNull(fetchBudget, "fetchBudget must not be null");
        String normalizedSenderEmail = senderEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedSenderEmail.isEmpty()) {
            throw new IllegalArgumentException("senderEmail must not be blank");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be strictly before to");
        }
        int effectiveLimit = Math.min(Math.max(limit, 0), HARD_LIMIT_MESSAGES);
        if (effectiveLimit == 0) return List.of();

        Gmail gmail = buildGmailClient(tenantId, fetchBudget);
        StringBuilder queryBuilder = new StringBuilder("from:").append(normalizedSenderEmail);
        if (archivedOnly) {
            queryBuilder.append(" -in:inbox");
        }
        if (from != null) {
            queryBuilder.append(" after:").append(from.getEpochSecond());
        }
        if (to != null) {
            queryBuilder.append(" before:").append(to.getEpochSecond());
        }
        String query = queryBuilder.toString();
        try {
            ListMessagesResponse listResponse =
                    gmail.users()
                            .messages()
                            .list("me")
                            .setQ(query)
                            .setMaxResults((long) effectiveLimit)
                            .setFields(LIST_FIELDS)
                            .execute();
            List<Message> messageReferences =
                    listResponse.getMessages() == null ? List.of() : listResponse.getMessages();
            if (messageReferences.isEmpty()) {
                log.info(
                        "event=cleanup_sender_messages_listed tenantId={} senderDomain={} archivedOnly={} count=0",
                        tenantId,
                        domainOf(normalizedSenderEmail),
                        archivedOnly);
                return List.of();
            }

            ArrayList<SenderMessageSummary> summaries = new ArrayList<>();
            for (Message messageReference : messageReferences) {
                if (messageReference == null || messageReference.getId() == null) continue;
                Message fullMessage =
                        gmail.users()
                                .messages()
                                .get("me", messageReference.getId())
                                .setFormat("metadata")
                                .setMetadataHeaders(SUMMARY_HEADERS)
                                .setFields(SUMMARY_FIELDS)
                                .execute();
                summaries.add(toSummary(fullMessage));
            }
            log.info(
                    "event=cleanup_sender_messages_listed tenantId={} senderDomain={} archivedOnly={} count={}",
                    tenantId,
                    domainOf(normalizedSenderEmail),
                    archivedOnly,
                    summaries.size());
            return List.copyOf(summaries);
        } catch (InvalidGrantException invalidGrantException) {
            throw new SenderMessagesUnavailableException(UnavailableReason.REVOKED);
        } catch (GoogleJsonResponseException googleResponseException) {
            if (googleResponseException.getStatusCode() == 401
                    || googleResponseException.getStatusCode() == 403) {
                throw new SenderMessagesUnavailableException(UnavailableReason.NO_READ_GRANT);
            }
            throw new SenderMessagesUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        } catch (IOException ioException) {
            throw new SenderMessagesUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        }
    }

    /**
     * Fetch a single message body via {@code users.messages.get format=full}. Returns {@code
     * Optional.empty()} if Gmail returns 404 (message was deleted between list and click).
     */
    public Optional<SenderMessageBody> fetchMessageBody(
            UUID tenantId, String gmailMessageId, Duration fetchBudget) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(fetchBudget, "fetchBudget must not be null");
        if (gmailMessageId.isBlank()) {
            throw new IllegalArgumentException("gmailMessageId must not be blank");
        }

        Gmail gmail = buildGmailClient(tenantId, fetchBudget);
        try {
            Message fullMessage =
                    gmail.users().messages().get("me", gmailMessageId).setFormat("full").execute();
            return Optional.of(toBody(fullMessage));
        } catch (InvalidGrantException invalidGrantException) {
            throw new SenderMessagesUnavailableException(UnavailableReason.REVOKED);
        } catch (GoogleJsonResponseException googleResponseException) {
            if (googleResponseException.getStatusCode() == 404) {
                return Optional.empty();
            }
            if (googleResponseException.getStatusCode() == 401
                    || googleResponseException.getStatusCode() == 403) {
                throw new SenderMessagesUnavailableException(UnavailableReason.NO_READ_GRANT);
            }
            throw new SenderMessagesUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        } catch (IOException ioException) {
            throw new SenderMessagesUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        }
    }

    private Gmail buildGmailClient(UUID tenantId, Duration fetchBudget) {
        GmailConnectionEntity connection =
                gmailConnectionRepository
                        .findByTenantId(tenantId)
                        .orElseThrow(
                                () ->
                                        new SenderMessagesUnavailableException(
                                                UnavailableReason.NOT_CONNECTED));
        if (connection.getStatus() != GmailConnectionStatus.CONNECTED) {
            throw new SenderMessagesUnavailableException(UnavailableReason.DISCONNECTED);
        }
        try {
            return gmailApiClientFactory.buildClientForConnection(
                    connection, tenantId, fetchBudget);
        } catch (InvalidGrantException invalidGrantException) {
            throw new SenderMessagesUnavailableException(UnavailableReason.REVOKED);
        } catch (IOException ioException) {
            throw new SenderMessagesUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        }
    }

    private static SenderMessageSummary toSummary(Message message) {
        MessagePart payload = message.getPayload();
        String subject =
                truncate(
                        GmailMessageHeaders.firstValue(payload, "Subject").orElse(""),
                        SUBJECT_MAX_LENGTH);
        String snippet =
                truncate(Objects.requireNonNullElse(message.getSnippet(), ""), SNIPPET_MAX_LENGTH);
        Instant internalDate =
                Instant.ofEpochMilli(
                        message.getInternalDate() == null ? 0L : message.getInternalDate());
        List<String> labelIds = message.getLabelIds() == null ? List.of() : message.getLabelIds();
        boolean archived = !labelIds.contains("INBOX");
        boolean unread = labelIds.contains("UNREAD");
        return new SenderMessageSummary(
                message.getId(),
                Objects.requireNonNullElse(message.getThreadId(), message.getId()),
                subject,
                snippet,
                internalDate,
                archived,
                unread);
    }

    private static SenderMessageBody toBody(Message message) {
        MessagePart payload = message.getPayload();
        String subject =
                truncate(
                        GmailMessageHeaders.firstValue(payload, "Subject").orElse(""),
                        SUBJECT_MAX_LENGTH);
        String fromHeader = GmailMessageHeaders.firstValue(payload, "From").orElse("");
        Instant internalDate =
                Instant.ofEpochMilli(
                        message.getInternalDate() == null ? 0L : message.getInternalDate());
        BodyParts parts = walkPayload(payload);
        return new SenderMessageBody(
                message.getId(),
                subject,
                fromHeader,
                internalDate,
                parts.htmlBody(),
                parts.plainBody());
    }

    private static BodyParts walkPayload(MessagePart payload) {
        if (payload == null) return new BodyParts(null, null);
        String htmlBody = null;
        String plainBody = null;
        String html = decodeBodyByMimeType(payload, "text/html");
        String plain = decodeBodyByMimeType(payload, "text/plain");
        if (html != null) htmlBody = html;
        if (plain != null) plainBody = plain;
        if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                BodyParts childBody = walkPayload(part);
                if (htmlBody == null && childBody.htmlBody() != null)
                    htmlBody = childBody.htmlBody();
                if (plainBody == null && childBody.plainBody() != null)
                    plainBody = childBody.plainBody();
                if (htmlBody != null && plainBody != null) break;
            }
        }
        return new BodyParts(htmlBody, plainBody);
    }

    private static String decodeBodyByMimeType(MessagePart part, String wantedMimeType) {
        if (part == null) return null;
        String mimeType = part.getMimeType();
        if (mimeType == null || !mimeType.equalsIgnoreCase(wantedMimeType)) return null;
        MessagePartBody body = part.getBody();
        if (body == null || body.getData() == null) return null;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(body.getData());
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException base64Failure) {
            return null;
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        String trimmed = text.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= maxLength) return trimmed;
        return trimmed.substring(0, maxLength);
    }

    private static String domainOf(String senderEmail) {
        int atIndex = senderEmail.indexOf('@');
        return atIndex < 0 ? "unknown" : senderEmail.substring(atIndex + 1);
    }

    private record BodyParts(String htmlBody, String plainBody) {}

    public enum UnavailableReason {
        NOT_CONNECTED,
        DISCONNECTED,
        REVOKED,
        NO_READ_GRANT,
        GMAIL_UNAVAILABLE
    }

    public static class SenderMessagesUnavailableException extends RuntimeException {
        private final UnavailableReason reason;

        public SenderMessagesUnavailableException(UnavailableReason reason) {
            super("Sender messages unavailable: " + reason);
            this.reason = Objects.requireNonNull(reason, "reason must not be null");
        }

        public UnavailableReason reason() {
            return reason;
        }
    }
}

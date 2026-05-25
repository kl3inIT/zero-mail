package com.zeromail.core.chat.usecases.tools;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.ChatToolCatalog.SearchInboxArgs;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GmailMessageHeaders;
import com.zeromail.core.tenant.TenantContext;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@SuppressWarnings("DuplicatedCode")
public class SearchInboxToolHandler implements ChatReadToolHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchInboxToolHandler.class);
    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final int MAX_RESULTS_CAP = 20;
    private static final List<String> METADATA_HEADERS = List.of("From", "To", "Cc", "Subject");
    private static final String METADATA_FIELDS =
            "id,threadId,labelIds,internalDate,snippet,payload/headers,payload/parts/filename";

    private final GmailApiClientFactory gmailApiClientFactory;
    private final ObjectMapper objectMapper;

    public SearchInboxToolHandler(
            GmailApiClientFactory gmailApiClientFactory, ObjectMapper objectMapper) {
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatToolName name() {
        return ChatToolName.SEARCH_INBOX;
    }

    @Override
    public String executeJson(String inputJson, String tenantId) {
        UUID boundTenantId = TenantContext.currentTenantUuid();
        ReadToolJson.requireTenantMatch(tenantId, boundTenantId);
        SearchInboxArgs args =
                ReadToolJson.readArgs(objectMapper, inputJson, SearchInboxArgs.class);
        int maxResults = boundedMaxResults(args.maxResults());
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(boundTenantId);
            ListMessagesResponse searchResponse =
                    gmail.users()
                            .messages()
                            .list("me")
                            .setQ(
                                    args.query() == null || args.query().isBlank()
                                            ? "in:anywhere"
                                            : args.query())
                            .setMaxResults((long) maxResults)
                            .execute();
            List<Message> messageRefs =
                    searchResponse.getMessages() == null ? List.of() : searchResponse.getMessages();
            ArrayList<SearchInboxMessage> messages = new ArrayList<>();
            for (Message messageRef : messageRefs) {
                if (messageRef == null || messageRef.getId() == null) {
                    continue;
                }
                Message gmailMessage =
                        gmail.users()
                                .messages()
                                .get("me", messageRef.getId())
                                .setFormat("metadata")
                                .setFields(METADATA_FIELDS)
                                .setMetadataHeaders(METADATA_HEADERS)
                                .execute();
                messages.add(toSearchMessage(gmailMessage));
            }
            log.info(
                    "event=chat_read_tool_executed tenantId={} toolName={} resultCount={}",
                    tenantId,
                    name().id(),
                    messages.size());
            return ReadToolJson.writeOutput(objectMapper, new SearchInboxOutput(messages));
        } catch (IOException ioException) {
            throw new IllegalStateException("Gmail search failed", ioException);
        }
    }

    private static int boundedMaxResults(Integer requestedMaxResults) {
        int effectiveMaxResults =
                requestedMaxResults == null || requestedMaxResults < 1
                        ? DEFAULT_MAX_RESULTS
                        : requestedMaxResults;
        return Math.min(effectiveMaxResults, MAX_RESULTS_CAP);
    }

    private static SearchInboxMessage toSearchMessage(Message gmailMessage) {
        MessagePart payload = gmailMessage.getPayload();
        return new SearchInboxMessage(
                gmailMessage.getId(),
                gmailMessage.getThreadId(),
                ReadToolJson.cap(
                        GmailMessageHeaders.firstValue(payload, "Subject").orElse(""), 120),
                ReadToolJson.cap(gmailMessage.getSnippet(), 80),
                ReadToolJson.cap(GmailMessageHeaders.firstValue(payload, "From").orElse(""), 320),
                parseRecipients(GmailMessageHeaders.firstValue(payload, "To").orElse("")),
                internalDate(gmailMessage),
                hasAttachment(payload));
    }

    static List<String> parseRecipients(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return List.of();
        }
        ArrayList<String> recipients = new ArrayList<>();
        for (String part : headerValue.split(",")) {
            String recipient = ReadToolJson.cap(part, 320);
            if (!recipient.isBlank()) {
                recipients.add(recipient);
            }
        }
        return List.copyOf(recipients);
    }

    static Instant internalDate(Message gmailMessage) {
        Long internalDateMillis = gmailMessage.getInternalDate();
        return internalDateMillis == null
                ? Instant.EPOCH
                : Instant.ofEpochMilli(internalDateMillis);
    }

    static boolean hasAttachment(MessagePart payload) {
        if (payload == null || payload.getParts() == null) {
            return false;
        }
        for (MessagePart part : payload.getParts()) {
            if (part.getFilename() != null && !part.getFilename().isBlank()) {
                return true;
            }
            if (hasAttachment(part)) {
                return true;
            }
        }
        return false;
    }

    public record SearchInboxOutput(List<SearchInboxMessage> messages) {}

    public record SearchInboxMessage(
            String messageId,
            String threadId,
            String subject,
            String snippet,
            String from,
            List<String> to,
            Instant date,
            boolean hasAttachment) {}
}

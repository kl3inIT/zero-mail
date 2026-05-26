package com.zeromail.core.chat.usecases.tools;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.Thread;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.ChatToolCatalog.GetThreadArgs;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GmailMessageHeaders;
import com.zeromail.core.tenant.TenantContext;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GetThreadToolHandler implements ChatReadToolHandler {

    private static final Logger log = LoggerFactory.getLogger(GetThreadToolHandler.class);
    private static final List<String> METADATA_HEADERS = List.of("From", "To", "Cc");
    private static final String THREAD_FIELDS =
            "id,messages(id,threadId,internalDate,payload/headers)";

    private final GmailApiClientFactory gmailApiClientFactory;
    private final ObjectMapper objectMapper;

    public GetThreadToolHandler(
            GmailApiClientFactory gmailApiClientFactory, ObjectMapper objectMapper) {
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatToolName name() {
        return ChatToolName.GET_THREAD;
    }

    @Override
    public String executeJson(String inputJson, String tenantId) {
        UUID boundTenantId = TenantContext.currentTenantUuid();
        ReadToolJson.requireTenantMatch(tenantId, boundTenantId);
        GetThreadArgs args = ReadToolJson.readArgs(objectMapper, inputJson, GetThreadArgs.class);
        if (args.threadId() == null || args.threadId().isBlank()) {
            throw new IllegalArgumentException("threadId must not be blank");
        }
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(boundTenantId);
            Thread gmailThread =
                    gmail.users()
                            .threads()
                            .get("me", args.threadId())
                            .setFormat("metadata")
                            .setFields(THREAD_FIELDS)
                            .setMetadataHeaders(METADATA_HEADERS)
                            .execute();
            GetThreadOutput output = toOutput(gmailThread);
            log.info(
                    "event=chat_read_tool_executed tenantId={} toolName={} resultCount={}",
                    tenantId,
                    name().id(),
                    output.messageIds().size());
            return ReadToolJson.writeOutput(objectMapper, output);
        } catch (IOException ioException) {
            throw new IllegalStateException("Gmail thread fetch failed", ioException);
        }
    }

    private static GetThreadOutput toOutput(Thread gmailThread) {
        List<Message> messages =
                gmailThread.getMessages() == null ? List.of() : gmailThread.getMessages();
        LinkedHashSet<String> participants = new LinkedHashSet<>();
        Instant lastActivityAt = Instant.EPOCH;
        for (Message gmailMessage : messages) {
            MessagePart payload = gmailMessage.getPayload();
            participants.addAll(
                    SearchInboxToolHandler.parseRecipients(
                            GmailMessageHeaders.firstValue(payload, "From").orElse("")));
            participants.addAll(
                    SearchInboxToolHandler.parseRecipients(
                            GmailMessageHeaders.firstValue(payload, "To").orElse("")));
            participants.addAll(
                    SearchInboxToolHandler.parseRecipients(
                            GmailMessageHeaders.firstValue(payload, "Cc").orElse("")));
            Instant messageDate = SearchInboxToolHandler.internalDate(gmailMessage);
            if (messageDate.isAfter(lastActivityAt)) {
                lastActivityAt = messageDate;
            }
        }
        return new GetThreadOutput(
                gmailThread.getId(),
                List.copyOf(participants),
                messages.stream().map(Message::getId).filter(java.util.Objects::nonNull).toList(),
                lastActivityAt);
    }

    public record GetThreadOutput(
            String threadId,
            List<String> participantList,
            List<String> messageIds,
            Instant lastActivityAt) {}
}

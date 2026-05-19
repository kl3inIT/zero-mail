package com.zeromail.core.chat.usecases.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.ChatToolCatalog.GetMessageArgs;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.tenant.TenantContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GetMessageToolHandler implements ChatReadToolHandler {

    private static final Logger log = LoggerFactory.getLogger(GetMessageToolHandler.class);
    private static final String FULL_FIELDS =
            "id,threadId,labelIds,internalDate,payload/headers,payload/body/data,payload/parts";

    private final GmailApiClientFactory gmailApiClientFactory;
    private final ObjectMapper objectMapper;

    public GetMessageToolHandler(
            GmailApiClientFactory gmailApiClientFactory, ObjectMapper objectMapper) {
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatToolName name() {
        return ChatToolName.GET_MESSAGE;
    }

    @Override
    public String executeJson(String inputJson, String tenantId) {
        UUID boundTenantId = TenantContext.currentTenantUuid();
        ReadToolJson.requireTenantMatch(tenantId, boundTenantId);
        GetMessageArgs args = ReadToolJson.readArgs(objectMapper, inputJson, GetMessageArgs.class);
        if (args.messageId() == null || args.messageId().isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(boundTenantId);
            Message gmailMessage =
                    gmail.users()
                            .messages()
                            .get("me", args.messageId())
                            .setFormat("full")
                            .setFields(FULL_FIELDS)
                            .execute();
            GetMessageOutput output = toOutput(gmailMessage);
            log.info(
                    "event=chat_read_tool_executed tenantId={} toolName={} resultCount={}",
                    tenantId,
                    name().id(),
                    1);
            return ReadToolJson.writeOutput(objectMapper, output);
        } catch (IOException ioException) {
            throw new IllegalStateException("Gmail message fetch failed", ioException);
        }
    }

    private static GetMessageOutput toOutput(Message gmailMessage) {
        MessagePart payload = gmailMessage.getPayload();
        return new GetMessageOutput(
                gmailMessage.getId(),
                gmailMessage.getThreadId(),
                SearchInboxToolHandler.headerValue(payload, "Subject"),
                SearchInboxToolHandler.headerValue(payload, "From"),
                SearchInboxToolHandler.parseRecipients(
                        SearchInboxToolHandler.headerValue(payload, "To")),
                SearchInboxToolHandler.parseRecipients(
                        SearchInboxToolHandler.headerValue(payload, "Cc")),
                SearchInboxToolHandler.internalDate(gmailMessage),
                headers(payload),
                decodedTextBody(payload));
    }

    private static Map<String, String> headers(MessagePart payload) {
        if (payload == null || payload.getHeaders() == null) {
            return Map.of();
        }
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        payload.getHeaders()
                .forEach(
                        header -> {
                            if (header.getName() != null && header.getValue() != null) {
                                headers.put(
                                        header.getName(),
                                        ReadToolJson.cap(header.getValue(), 1000));
                            }
                        });
        return Map.copyOf(headers);
    }

    private static String decodedTextBody(MessagePart payload) {
        if (payload == null) {
            return "";
        }
        if (isTextPart(payload)) {
            String decodedBody = decodeBody(payload.getBody());
            if (!decodedBody.isBlank()) {
                return decodedBody;
            }
        }
        if (payload.getParts() == null) {
            return "";
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (MessagePart part : payload.getParts()) {
            String partBody = decodedTextBody(part);
            if (!partBody.isBlank()) {
                if (!bodyBuilder.isEmpty()) {
                    bodyBuilder.append("\n\n");
                }
                bodyBuilder.append(partBody);
            }
        }
        return bodyBuilder.toString();
    }

    private static boolean isTextPart(MessagePart payload) {
        String mimeType = payload.getMimeType();
        return mimeType == null || mimeType.equalsIgnoreCase("text/plain");
    }

    private static String decodeBody(MessagePartBody body) {
        if (body == null || body.getData() == null || body.getData().isBlank()) {
            return "";
        }
        byte[] decodedBytes = Base64.getUrlDecoder().decode(body.getData());
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }

    public record GetMessageOutput(
            String messageId,
            String threadId,
            String subject,
            String from,
            List<String> to,
            List<String> cc,
            Instant date,
            Map<String, String> headers,
            @JsonProperty("bodyText") String decodedTextBody) {}
}

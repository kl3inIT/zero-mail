package com.zeromail.core.chat.usecases.tools;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.tenant.TenantContext;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ListLabelsToolHandler implements ChatReadToolHandler {

    private static final Logger log = LoggerFactory.getLogger(ListLabelsToolHandler.class);

    private final GmailApiClientFactory gmailApiClientFactory;
    private final ObjectMapper objectMapper;

    public ListLabelsToolHandler(
            GmailApiClientFactory gmailApiClientFactory, ObjectMapper objectMapper) {
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatToolName name() {
        return ChatToolName.LIST_LABELS;
    }

    @Override
    public String executeJson(String inputJson, String tenantId) {
        UUID boundTenantId = TenantContext.currentTenantUuid();
        ReadToolJson.requireTenantMatch(tenantId, boundTenantId);
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(boundTenantId);
            ListLabelsResponse labelsResponse = gmail.users().labels().list("me").execute();
            List<LabelOutput> labels =
                    (labelsResponse.getLabels() == null
                                    ? List.<Label>of()
                                    : labelsResponse.getLabels())
                            .stream()
                                    .map(
                                            label ->
                                                    new LabelOutput(
                                                            label.getId(),
                                                            label.getName(),
                                                            label.getType()))
                                    .toList();
            log.info(
                    "event=chat_read_tool_executed tenantId={} toolName={} resultCount={}",
                    tenantId,
                    name().id(),
                    labels.size());
            return ReadToolJson.writeOutput(objectMapper, new ListLabelsOutput(labels));
        } catch (IOException ioException) {
            throw new IllegalStateException("Gmail label list failed", ioException);
        }
    }

    public record ListLabelsOutput(List<LabelOutput> labels) {}

    public record LabelOutput(String id, String name, String type) {}
}

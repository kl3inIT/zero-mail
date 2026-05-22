package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.ChatToolCatalog.GetSenderSafetyEntryArgs;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.SenderSafetyEntryService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GetSenderSafetyEntryToolHandler implements ChatReadToolHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GetSenderSafetyEntryToolHandler.class);

    private final SenderSafetyEntryService senderSafetyEntryService;
    private final ObjectMapper objectMapper;

    public GetSenderSafetyEntryToolHandler(
            SenderSafetyEntryService senderSafetyEntryService, ObjectMapper objectMapper) {
        this.senderSafetyEntryService = senderSafetyEntryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatToolName name() {
        return ChatToolName.GET_SENDER_SAFETY_ENTRY;
    }

    @Override
    public String executeJson(String inputJson, String tenantId) {
        UUID boundTenantId = TenantContext.currentTenantUuid();
        ReadToolJson.requireTenantMatch(tenantId, boundTenantId);
        GetSenderSafetyEntryArgs args =
                ReadToolJson.readArgs(objectMapper, inputJson, GetSenderSafetyEntryArgs.class);
        SenderSafetyEntryService.SenderSafetyEntry output =
                senderSafetyEntryService.find(boundTenantId, args.senderEmail());
        log.info(
                "event=chat_read_tool_executed tenantId={} toolName={} resultCount={}",
                tenantId,
                name().id(),
                output.mode().equals("not_found") ? 0 : 1);
        return ReadToolJson.writeOutput(objectMapper, output);
    }
}

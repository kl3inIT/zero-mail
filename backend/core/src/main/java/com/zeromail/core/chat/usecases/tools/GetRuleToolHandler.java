package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.ChatToolCatalog.GetRuleArgs;
import com.zeromail.core.rules.usecases.RuleManagementService;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GetRuleToolHandler implements ChatReadToolHandler {

    private static final Logger log = LoggerFactory.getLogger(GetRuleToolHandler.class);

    private final RuleManagementService ruleManagementService;
    private final ObjectMapper objectMapper;

    public GetRuleToolHandler(
            RuleManagementService ruleManagementService, ObjectMapper objectMapper) {
        this.ruleManagementService = ruleManagementService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatToolName name() {
        return ChatToolName.GET_RULE;
    }

    @Override
    public String executeJson(String inputJson, String tenantId) {
        UUID boundTenantId = TenantContext.currentTenantUuid();
        ReadToolJson.requireTenantMatch(tenantId, boundTenantId);
        GetRuleArgs args = ReadToolJson.readArgs(objectMapper, inputJson, GetRuleArgs.class);
        if (args.ruleId() == null) {
            throw new IllegalArgumentException("ruleId must not be null");
        }
        ListRulesToolHandler.RuleOutput rule =
                ListRulesToolHandler.toOutput(
                        ruleManagementService.get(boundTenantId, args.ruleId()));
        log.info(
                "event=chat_read_tool_executed tenantId={} toolName={} resultCount={}",
                tenantId,
                name().id(),
                1);
        return ReadToolJson.writeOutput(objectMapper, new GetRuleOutput(rule));
    }

    public record GetRuleOutput(ListRulesToolHandler.RuleOutput rule) {}
}

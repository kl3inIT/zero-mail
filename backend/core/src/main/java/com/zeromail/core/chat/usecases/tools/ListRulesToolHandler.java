package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.rules.projection.RuleStatusProjection;
import com.zeromail.core.rules.usecases.RuleManagementService;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ListRulesToolHandler implements ChatReadToolHandler {

    private static final Logger log = LoggerFactory.getLogger(ListRulesToolHandler.class);

    private final RuleManagementService ruleManagementService;
    private final ObjectMapper objectMapper;

    public ListRulesToolHandler(
            RuleManagementService ruleManagementService, ObjectMapper objectMapper) {
        this.ruleManagementService = ruleManagementService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatToolName name() {
        return ChatToolName.LIST_RULES;
    }

    @Override
    public String executeJson(String inputJson, String tenantId) {
        UUID boundTenantId = TenantContext.currentTenantUuid();
        ReadToolJson.requireTenantMatch(tenantId, boundTenantId);
        List<RuleOutput> rules =
                ruleManagementService.listOrdered(boundTenantId).stream()
                        .map(ListRulesToolHandler::toOutput)
                        .toList();
        log.info(
                "event=chat_read_tool_executed tenantId={} toolName={} resultCount={}",
                tenantId,
                name().id(),
                rules.size());
        return ReadToolJson.writeOutput(objectMapper, new ListRulesOutput(rules));
    }

    static RuleOutput toOutput(RuleStatusProjection ruleStatusProjection) {
        return new RuleOutput(
                ruleStatusProjection.ruleId().value(),
                ruleStatusProjection.displayName(),
                ruleStatusProjection.sourceText(),
                ruleStatusProjection.enabled(),
                ruleStatusProjection.orderIndex(),
                ruleStatusProjection.sourceLanguage().id(),
                ruleStatusProjection.schemaVersion().id(),
                ruleStatusProjection.matcherAst(),
                ruleStatusProjection.actionIntents(),
                ruleStatusProjection.entityVersion(),
                ruleStatusProjection.lastPreviewedAt());
    }

    public record ListRulesOutput(List<RuleOutput> rules) {}

    public record RuleOutput(
            UUID ruleId,
            String displayName,
            String sourceText,
            boolean enabled,
            int orderIndex,
            String sourceLanguage,
            String schemaVersion,
            String matcher,
            String actions,
            Integer entityVersion,
            Instant lastPreviewedAt) {}
}

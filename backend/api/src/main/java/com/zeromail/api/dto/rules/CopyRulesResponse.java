package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.CopyRulesService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"copiedCount", "copiedRuleIds"})
public record CopyRulesResponse(int copiedCount, List<UUID> copiedRuleIds) {

    public CopyRulesResponse {
        copiedRuleIds = List.copyOf(copiedRuleIds);
    }

    public static CopyRulesResponse from(CopyRulesService.CopyRulesResult copyRulesResult) {
        return new CopyRulesResponse(
                copyRulesResult.copiedCount(),
                copyRulesResult.copiedRules().stream()
                        .map(copiedRule -> copiedRule.ruleId().value())
                        .toList());
    }
}

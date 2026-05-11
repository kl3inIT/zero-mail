package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.application.RulePreviewResult;
import java.util.List;
import java.util.UUID;

public record ActionChipResponse(
        String actionTypeId,
        String safeLabel,
        List<UUID> contributingRuleIds,
        List<String> evidenceIds) {

    static ActionChipResponse from(RulePreviewResult.ActionChip actionChip) {
        return new ActionChipResponse(
                actionChip.actionTypeId(),
                actionChip.safeLabel(),
                actionChip.contributingRuleIds(),
                actionChip.evidenceIds());
    }

    public ActionChipResponse {
        contributingRuleIds = List.copyOf(contributingRuleIds);
        evidenceIds = List.copyOf(evidenceIds);
    }
}

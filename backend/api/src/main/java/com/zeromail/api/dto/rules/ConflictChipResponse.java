package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RulePreviewResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(requiredProperties = {"conflictTypeId", "contributingRuleIds", "metadata"})
public record ConflictChipResponse(
        String conflictTypeId, List<UUID> contributingRuleIds, Map<String, String> metadata) {

    static ConflictChipResponse from(RulePreviewResult.ConflictChip conflictChip) {
        return new ConflictChipResponse(
                conflictChip.conflictTypeId(),
                conflictChip.contributingRuleIds(),
                conflictChip.metadata());
    }

    public ConflictChipResponse {
        contributingRuleIds = List.copyOf(contributingRuleIds);
        metadata = Map.copyOf(metadata);
    }
}

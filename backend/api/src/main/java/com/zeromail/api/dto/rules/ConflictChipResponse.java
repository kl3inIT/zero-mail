package com.zeromail.api.dto.rules;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.zeromail.core.rules.application.RulePreviewResult;

public record ConflictChipResponse(
    String conflictTypeId, List<UUID> contributingRuleIds, Map<String, String> metadata) {

  static ConflictChipResponse from(RulePreviewResult.ConflictChip conflictChip) {
    return new ConflictChipResponse(
        conflictChip.conflictTypeId(), conflictChip.contributingRuleIds(), conflictChip.metadata());
  }

  public ConflictChipResponse {
    contributingRuleIds = List.copyOf(contributingRuleIds);
    metadata = Map.copyOf(metadata);
  }
}

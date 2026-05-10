package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.application.RulePreviewResult;

public record EvidenceChipResponse(String matcherNodeId, String reasonKey) {

  static EvidenceChipResponse from(RulePreviewResult.EvidenceChip evidenceChip) {
    return new EvidenceChipResponse(evidenceChip.matcherNodeId(), evidenceChip.reasonKey());
  }
}

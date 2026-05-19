package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RulePreviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"matcherNodeId", "reasonKey"})
public record EvidenceChipResponse(String matcherNodeId, String reasonKey) {

    static EvidenceChipResponse from(RulePreviewResult.EvidenceChip evidenceChip) {
        return new EvidenceChipResponse(evidenceChip.matcherNodeId(), evidenceChip.reasonKey());
    }
}

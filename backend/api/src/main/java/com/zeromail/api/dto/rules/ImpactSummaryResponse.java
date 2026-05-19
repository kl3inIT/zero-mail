package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RulePreviewResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(
        requiredProperties = {
            "sampleSize",
            "sampledMessageCount",
            "matchedCount",
            "proposedActionCounts",
            "deferredCount",
            "conflictCount",
            "noWriteNotice",
            "noWriteNoticeKey"
        })
public record ImpactSummaryResponse(
        int sampleSize,
        int sampledMessageCount,
        int matchedCount,
        Map<String, Integer> proposedActionCounts,
        int deferredCount,
        int conflictCount,
        boolean noWriteNotice,
        @Schema(nullable = true) String noWriteNoticeKey) {

    static ImpactSummaryResponse from(RulePreviewResult.ImpactSummary impactSummary) {
        return new ImpactSummaryResponse(
                impactSummary.sampleSize(),
                impactSummary.sampledMessageCount(),
                impactSummary.matchedCount(),
                impactSummary.proposedActionCounts(),
                impactSummary.deferredCount(),
                impactSummary.conflictCount(),
                impactSummary.noWriteNotice(),
                impactSummary.noWriteNoticeKey());
    }

    public ImpactSummaryResponse {
        proposedActionCounts = Map.copyOf(proposedActionCounts);
    }
}

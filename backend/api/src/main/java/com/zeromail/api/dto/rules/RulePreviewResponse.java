package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.application.RulePreviewResult;
import java.util.List;

public record RulePreviewResponse(
        ImpactSummaryResponse impactSummary,
        List<PreviewRowResponse> rows,
        boolean savedRuleMarkedPreviewed) {

    public static RulePreviewResponse from(RulePreviewResult previewResult) {
        return new RulePreviewResponse(
                ImpactSummaryResponse.from(previewResult.impactSummary()),
                previewResult.rows().stream().map(PreviewRowResponse::from).toList(),
                previewResult.savedRuleMarkedPreviewed());
    }

    public RulePreviewResponse {
        rows = List.copyOf(rows);
    }
}

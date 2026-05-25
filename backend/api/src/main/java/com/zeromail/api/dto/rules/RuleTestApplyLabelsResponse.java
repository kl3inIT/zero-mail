package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleTestApplyService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        requiredProperties = {
            "preview",
            "appliedLabelCount",
            "affectedMessageCount",
            "appliedLabels"
        })
public record RuleTestApplyLabelsResponse(
        RulePreviewResponse preview,
        int appliedLabelCount,
        int affectedMessageCount,
        List<AppliedRuleLabelResponse> appliedLabels) {

    public static RuleTestApplyLabelsResponse from(
            RuleTestApplyService.RuleTestApplyResult applyResult) {
        return new RuleTestApplyLabelsResponse(
                RulePreviewResponse.from(applyResult.previewResult()),
                applyResult.appliedLabelCount(),
                applyResult.affectedMessageCount(),
                applyResult.appliedLabels().stream().map(AppliedRuleLabelResponse::from).toList());
    }

    public RuleTestApplyLabelsResponse {
        appliedLabels = List.copyOf(appliedLabels);
    }
}

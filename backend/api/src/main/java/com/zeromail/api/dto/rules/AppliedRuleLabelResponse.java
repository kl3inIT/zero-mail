package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleTestApplyService;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"gmailMessageId", "gmailThreadId", "labelName", "gmailLabelId"})
public record AppliedRuleLabelResponse(
        String gmailMessageId, String gmailThreadId, String labelName, String gmailLabelId) {

    public static AppliedRuleLabelResponse from(RuleTestApplyService.AppliedLabel appliedLabel) {
        return new AppliedRuleLabelResponse(
                appliedLabel.gmailMessageId(),
                appliedLabel.gmailThreadId(),
                appliedLabel.labelName(),
                appliedLabel.gmailLabelId());
    }
}

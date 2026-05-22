package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.projection.RuleAutomationSettingsView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(requiredProperties = {"autoSendRulesEnabled"})
public record RuleAutomationSettingsResponse(boolean autoSendRulesEnabled) {

    public static RuleAutomationSettingsResponse from(
            RuleAutomationSettingsView ruleAutomationSettingsView) {
        Objects.requireNonNull(ruleAutomationSettingsView, "ruleAutomationSettingsView");
        return new RuleAutomationSettingsResponse(
                ruleAutomationSettingsView.autoSendRulesEnabled());
    }
}

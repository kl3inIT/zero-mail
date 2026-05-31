package com.zeromail.api.dto.settings;

import com.zeromail.core.chat.usecases.settings.SettingsBehaviorResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"autoDraftReplies", "draftConfidence", "sensitiveDataProtection"})
public record BehaviorSettingsResponse(
        boolean autoDraftReplies,
        @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}) String draftConfidence,
        boolean sensitiveDataProtection) {

    public static BehaviorSettingsResponse from(SettingsBehaviorResult settingsBehaviorResult) {
        return new BehaviorSettingsResponse(
                settingsBehaviorResult.autoDraftReplies(),
                settingsBehaviorResult.draftConfidence(),
                settingsBehaviorResult.sensitiveDataProtection());
    }
}

package com.zeromail.api.dto.admin.rulecatalog;

import com.zeromail.core.rules.catalog.usecases.RulePersonaWriteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(
        requiredProperties = {
            "personaKey",
            "displayNameEn",
            "displayNameVi",
            "displayOrder",
            "enabled"
        })
public record RuleCatalogPersonaWriteRequest(
        @NotBlank String personaKey,
        @NotBlank String displayNameEn,
        @NotBlank String displayNameVi,
        String icon,
        @Min(0) int displayOrder,
        boolean enabled,
        String reason) {

    public RulePersonaWriteCommand toCommand() {
        return new RulePersonaWriteCommand(
                personaKey, displayNameEn, displayNameVi, icon, displayOrder, enabled);
    }
}

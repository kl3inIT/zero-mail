package com.zeromail.api.dto.admin.rulecatalog;

import com.zeromail.core.rules.catalog.usecases.RulePromptWriteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(
        requiredProperties = {
            "exampleTextEn",
            "exampleTextVi",
            "displayOrder",
            "enabled",
            "sourceRef",
            "reason"
        })
public record RuleCatalogExampleWriteRequest(
        @NotBlank String exampleTextEn,
        @NotBlank String exampleTextVi,
        @Min(0) int displayOrder,
        boolean enabled,
        @NotBlank String sourceRef,
        @NotBlank String reason) {

    public RulePromptWriteCommand toCommand() {
        return new RulePromptWriteCommand(
                exampleTextEn, exampleTextVi, displayOrder, enabled, sourceRef);
    }
}

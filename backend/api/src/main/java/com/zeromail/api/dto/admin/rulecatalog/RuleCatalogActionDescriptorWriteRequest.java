package com.zeromail.api.dto.admin.rulecatalog;

import com.zeromail.core.rules.catalog.usecases.RuleActionDescriptorWriteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(
        requiredProperties = {
            "labelEn",
            "labelVi",
            "descriptionEn",
            "descriptionVi",
            "riskLevel",
            "availabilityStatus",
            "displayOrder",
            "enabled"
        })
public record RuleCatalogActionDescriptorWriteRequest(
        @NotBlank String labelEn,
        @NotBlank String labelVi,
        @NotBlank String descriptionEn,
        @NotBlank String descriptionVi,
        @NotBlank String riskLevel,
        @NotBlank String availabilityStatus,
        @Min(0) int displayOrder,
        boolean enabled,
        String reason) {

    public RuleActionDescriptorWriteCommand toCommand(String actionKey) {
        return new RuleActionDescriptorWriteCommand(
                actionKey,
                labelEn,
                labelVi,
                descriptionEn,
                descriptionVi,
                riskLevel,
                availabilityStatus,
                displayOrder,
                enabled);
    }
}

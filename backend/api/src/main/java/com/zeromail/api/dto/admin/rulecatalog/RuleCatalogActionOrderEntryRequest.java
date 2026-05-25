package com.zeromail.api.dto.admin.rulecatalog;

import com.zeromail.core.rules.catalog.usecases.RuleActionDescriptorOrderEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(requiredProperties = {"actionKey", "displayOrder"})
public record RuleCatalogActionOrderEntryRequest(
        @NotBlank String actionKey, @Min(0) int displayOrder) {

    public RuleActionDescriptorOrderEntry toCommand() {
        return new RuleActionDescriptorOrderEntry(actionKey, displayOrder);
    }
}

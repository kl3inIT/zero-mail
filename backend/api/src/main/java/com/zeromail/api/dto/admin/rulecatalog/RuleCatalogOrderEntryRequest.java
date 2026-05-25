package com.zeromail.api.dto.admin.rulecatalog;

import com.zeromail.core.rules.catalog.usecases.RuleCatalogOrderEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(requiredProperties = {"itemId", "displayOrder"})
public record RuleCatalogOrderEntryRequest(@NotNull UUID itemId, @Min(0) int displayOrder) {

    public RuleCatalogOrderEntry toCommand() {
        return new RuleCatalogOrderEntry(itemId, displayOrder);
    }
}

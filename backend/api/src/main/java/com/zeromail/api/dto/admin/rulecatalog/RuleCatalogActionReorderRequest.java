package com.zeromail.api.dto.admin.rulecatalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(requiredProperties = {"items", "reason"})
public record RuleCatalogActionReorderRequest(
        @Valid @NotEmpty List<RuleCatalogActionOrderEntryRequest> items, @NotBlank String reason) {

    public RuleCatalogActionReorderRequest {
        items = List.copyOf(items);
    }
}

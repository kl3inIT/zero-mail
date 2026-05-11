package com.zeromail.api.dto.rules;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RuleReorderRequest(@NotEmpty @Valid List<RuleOrderEntryRequest> entries) {

    public RuleReorderRequest {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}

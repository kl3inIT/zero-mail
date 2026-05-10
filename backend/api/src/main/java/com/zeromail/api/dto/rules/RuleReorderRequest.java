package com.zeromail.api.dto.rules;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record RuleReorderRequest(@NotEmpty @Valid List<RuleOrderEntryRequest> entries) {

  public RuleReorderRequest {
    entries = entries == null ? List.of() : List.copyOf(entries);
  }
}

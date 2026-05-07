package com.zeromail.api.dto.llm;

import com.zeromail.core.llm.model.BYOKProvider;

import jakarta.validation.constraints.NotNull;

public record ByokValidateRequest(
    @NotNull BYOKProvider provider, String endpoint, @NotNull String apiKey) {}

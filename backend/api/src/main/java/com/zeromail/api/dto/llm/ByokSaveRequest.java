package com.zeromail.api.dto.llm;

import com.zeromail.core.llm.domain.ByokProviderPreset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ByokSaveRequest(
        @NotNull ByokProviderPreset preset,
        String endpoint,
        @NotBlank String model,
        @NotNull String apiKey) {}

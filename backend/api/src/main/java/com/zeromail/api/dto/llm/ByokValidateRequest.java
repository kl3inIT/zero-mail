package com.zeromail.api.dto.llm;

import com.zeromail.core.llm.domain.ByokProviderPreset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"preset", "model", "apiKey"})
public record ByokValidateRequest(
        @NotNull ByokProviderPreset preset,
        String endpoint,
        @NotBlank String model,
        @NotNull String apiKey) {}

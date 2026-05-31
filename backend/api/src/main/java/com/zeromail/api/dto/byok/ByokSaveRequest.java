package com.zeromail.api.dto.byok;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"provider", "baseUrl", "apiKey"})
public record ByokSaveRequest(
        @NotBlank @Pattern(regexp = "^(OPENAI|ANTHROPIC|GOOGLE|DEEPSEEK|OPENROUTER|ROUTER_9R)$")
                @Schema(allowableValues = {"OPENAI", "ANTHROPIC", "GOOGLE", "DEEPSEEK"})
                String provider,
        @NotBlank @Size(min = 8, max = 255) String baseUrl,
        @NotBlank @Size(min = 8, max = 256) String apiKey,
        @Size(max = 64) String modelId) {}

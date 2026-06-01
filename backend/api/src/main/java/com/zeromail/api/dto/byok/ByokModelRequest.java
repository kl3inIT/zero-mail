package com.zeromail.api.dto.byok;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"modelId"})
public record ByokModelRequest(@NotBlank @Size(max = 64) String modelId) {}

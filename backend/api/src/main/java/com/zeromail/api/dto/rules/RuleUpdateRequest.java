package com.zeromail.api.dto.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "gmailConnectionId",
            "displayName",
            "sourceText",
            "compiled",
            "entityVersion"
        })
public record RuleUpdateRequest(
        @NotNull UUID gmailConnectionId,
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Size(max = 4000) String sourceText,
        @Valid @NotNull CompiledPayloadRequest compiled,
        @NotNull @PositiveOrZero Integer entityVersion) {}

package com.zeromail.api.dto.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
        requiredProperties = {
            "status",
            "sourceLanguage",
            "schemaVersion",
            "matcherAst",
            "actionIntents"
        })
public record CompiledPayloadRequest(
        @NotBlank String status,
        String sourceLanguage,
        String schemaVersion,
        String matcherAst,
        String actionIntents) {}

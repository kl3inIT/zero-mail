package com.zeromail.api.dto.rules;

import jakarta.validation.constraints.NotBlank;

public record CompiledPayloadRequest(
    @NotBlank String status,
    String sourceLanguage,
    String schemaVersion,
    String matcherAst,
    String actionIntents) {}

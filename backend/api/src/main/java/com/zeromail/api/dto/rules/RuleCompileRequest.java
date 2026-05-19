package com.zeromail.api.dto.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = "sourceText")
public record RuleCompileRequest(
        @NotBlank @Size(max = 4000) String sourceText,
        @Size(max = 1000) String clarificationAnswer,
        @Size(max = 2000) String priorCompileContext,
        @Size(max = 4000) String priorDraftJson,
        @Size(max = 500) String editInstruction) {}

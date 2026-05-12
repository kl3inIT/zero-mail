package com.zeromail.api.dto.rules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RuleCompileRequest(
        @NotBlank @Size(max = 4000) String sourceText,
        @Size(max = 1000) String clarificationAnswer,
        @Size(max = 2000) String priorCompileContext) {}
